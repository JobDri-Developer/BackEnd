package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.jobdri.jobdri_api.domain.analysis.application.model.AnalysisExecutionPayload;
import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisHighlightResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.Analysis;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysis;
import com.jobdri.jobdri_api.domain.analysis.type.QuestionAnalysisStatus;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionAnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.analysis.service.sanitization.AnalysisResultSanitizationService;
import com.jobdri.jobdri_api.domain.analysis.service.sanitization.AnalysisSanitizationRules;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.COMPLETENESS_WEIGHT;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.IMPACT_WEIGHT;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.JOB_FIT_WEIGHT;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_ANALYSES_PER_QUESTION;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_SCORE;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MIN_SCORE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisResultPersistenceService {
    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;
    private final AnalysisRepository analysisRepository;
    private final QuestionAnalysisRepository questionAnalysisRepository;
    private final AnalysisInputFingerprintProvider analysisInputFingerprintProvider;
    private final AnalysisResponseAssembler analysisResponseAssembler;
    private final AnalysisResultSanitizationService analysisResultSanitizationService;

    @Transactional
    public AnalysisResponse finalizeAnalysis(
            MockApply mockApply,
            List<Question> questions,
            List<AnalysisExecutionPayload.AnswerSnapshot> payloadSnapshots,
            AnalysisLlmResponse llmResponse,
            String inputFingerprint
    ) {
        MockApply lockedMockApply = mockApplyRepository.findByIdForUpdate(mockApply.getId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.MOCK_APPLY_NOT_FOUND,
                        "해당 모의 서류 지원을 찾을 수 없습니다. mockApplyId=" + mockApply.getId()
                ));
        VerifiedAnswerSnapshot answerSnapshot = verifyAnswerSnapshot(questions, payloadSnapshots);
        validateRequiredScores(llmResponse);
        int jobFit = validateScore("jobFit", llmResponse.jobFit());
        int impact = validateScore("impact", llmResponse.impact());
        int completeness = validateScore("completeness", llmResponse.completeness());
        List<AnalysisHighlightResponse> keyStrengths = analysisResultSanitizationService.buildHighlights(
                llmResponse.keyStrengths()
        );
        List<AnalysisHighlightResponse> keyWeaknesses = analysisResultSanitizationService.buildNonOverlappingHighlights(
                llmResponse.keyWeaknesses(),
                keyStrengths
        );
        List<MissingKeywordResponse> missingKeywords = analysisResultSanitizationService.buildMissingKeywords(
                lockedMockApply.getJobPosting(),
                answerSnapshot.combinedAnswers(),
                llmResponse
        );
        replaceExistingAnalysis(lockedMockApply);

        Analysis analysis = analysisRepository.save(Analysis.create(
                lockedMockApply,
                calculateScore(jobFit, impact, completeness),
                jobFit,
                impact,
                completeness,
                normalizeFeedback(llmResponse.feedback()),
                analysisResultSanitizationService.serializeMissingKeywords(missingKeywords),
                analysisResultSanitizationService.serializeHighlights(keyStrengths, "keyStrengths"),
                analysisResultSanitizationService.serializeHighlights(keyWeaknesses, "keyWeaknesses"),
                inputFingerprint
        ));

        List<QuestionAnalysis> questionAnalyses = buildQuestionAnalyses(
                analysis,
                questions,
                answerSnapshot.answerByQuestionId(),
                llmResponse
        );
        questionAnalysisRepository.saveAll(questionAnalyses);
        lockedMockApply.updateStatus(MockApplyStatus.COMPLETED);
        mockApplyRepository.flush();

        return analysisResponseAssembler.toResponse(
                lockedMockApply,
                analysis,
                questions,
                questionAnalyses,
                analysisResultSanitizationService.analysisResultPayload(analysis)
        );
    }

    @Transactional
    public AnalysisResponse getPersistedAnalysis(MockApply mockApply) {
        Analysis analysis = analysisRepository.findByMockApplyId(mockApply.getId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.ANALYSIS_NOT_FOUND,
                        "해당 모의 서류 지원의 분석 결과를 찾을 수 없습니다. mockApplyId=" + mockApply.getId()
                ));
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        List<QuestionAnalysis> questionAnalyses =
                questionAnalysisRepository.findAllByAnalysisIdOrderByQuestionIdAscIdAsc(analysis.getId());

        return analysisResponseAssembler.toResponse(
                mockApply,
                analysis,
                questions,
                questionAnalyses,
                analysisResultSanitizationService.sanitizeAndPersistAnalysisPayload(analysis, true)
        );
    }

    @Transactional(readOnly = true)
    public AnalysisResponse getPersistedAnalysis(MockApply mockApply, Analysis analysis) {
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        List<QuestionAnalysis> questionAnalyses =
                questionAnalysisRepository.findAllByAnalysisIdOrderByQuestionIdAscIdAsc(analysis.getId());

        return analysisResponseAssembler.toResponse(
                mockApply,
                analysis,
                questions,
                questionAnalyses,
                analysisResultSanitizationService.sanitizeAndPersistAnalysisPayload(analysis, false)
        );
    }

    private void replaceExistingAnalysis(MockApply mockApply) {
        Optional<Analysis> existingAnalysis = analysisRepository.findByMockApplyId(mockApply.getId());
        if (existingAnalysis.isEmpty()) {
            return;
        }

        Analysis analysis = existingAnalysis.get();
        mockApply.clearAnalysis();
        questionAnalysisRepository.deleteAllByAnalysisId(analysis.getId());
        analysisRepository.delete(analysis);
        analysisRepository.flush();
    }

    private VerifiedAnswerSnapshot verifyAnswerSnapshot(
            List<Question> databaseQuestions,
            List<AnalysisExecutionPayload.AnswerSnapshot> payloadSnapshots
    ) {
        String databaseFingerprint = analysisInputFingerprintProvider
                .createAnswerFingerprintFromQuestions(databaseQuestions);
        String payloadFingerprint = analysisInputFingerprintProvider
                .createAnswerFingerprint(payloadSnapshots);
        if (!databaseFingerprint.equals(payloadFingerprint)) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "분석 실행 이후 자소서 답변이 변경되어 결과를 저장할 수 없습니다."
            );
        }

        List<AnalysisExecutionPayload.AnswerSnapshot> immutableSnapshots = List.copyOf(payloadSnapshots);
        Map<Long, String> answerByQuestionId = new LinkedHashMap<>();
        for (AnalysisExecutionPayload.AnswerSnapshot snapshot : immutableSnapshots) {
            if (snapshot == null || snapshot.questionId() == null || !StringUtils.hasText(snapshot.answer())) {
                continue;
            }
            if (answerByQuestionId.putIfAbsent(snapshot.questionId(), snapshot.answer()) != null) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "분석 답변 snapshot에 중복된 questionId가 있습니다. questionId=" + snapshot.questionId()
                );
            }
        }
        return new VerifiedAnswerSnapshot(immutableSnapshots, Map.copyOf(answerByQuestionId));
    }

    private record VerifiedAnswerSnapshot(
            List<AnalysisExecutionPayload.AnswerSnapshot> answers,
            Map<Long, String> answerByQuestionId
    ) {
        private String combinedAnswers() {
            return answers.stream()
                    .map(AnalysisExecutionPayload.AnswerSnapshot::answer)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("\n"));
        }
    }

    private List<QuestionAnalysis> buildQuestionAnalyses(
            Analysis analysis,
            List<Question> questions,
            Map<Long, String> answerByQuestionId,
            AnalysisLlmResponse llmResponse
    ) {
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        List<QuestionAnalysis> result = new ArrayList<>();
        Map<Long, Integer> analysisCountByQuestionId = new HashMap<>();
        Map<Long, Integer> nextSearchIndexByQuestionId = new HashMap<>();
        Set<String> seenSentences = new HashSet<>();
        Set<Long> fabricatedQuestionIds = new HashSet<>();
        Set<String> keyStrengthQuotes = normalizedKeyStrengthQuotes(llmResponse);

        if (llmResponse.questionAnalyses() == null) {
            return result;
        }

        for (AnalysisLlmResponse.QuestionAnalysisItem item : llmResponse.questionAnalyses()) {
            if (item == null || item.questionId() == null || !StringUtils.hasText(item.sentence())) {
                continue;
            }

            Question question = questionMap.get(item.questionId());
            if (question == null) {
                continue;
            }

            String answer = answerByQuestionId.get(item.questionId());
            if (!StringUtils.hasText(answer)) {
                continue;
            }
            QuestionAnalysisStatus status = parseStatus(item.status());
            if (status == null || status == QuestionAnalysisStatus.MISSING) {
                continue;
            }
            if (status == QuestionAnalysisStatus.PROVEN
                    && !AnalysisSanitizationRules.hasValidProvenReason(item.reason())) {
                continue;
            }
            if (status == QuestionAnalysisStatus.FABRICATED
                    && !AnalysisSanitizationRules.hasFabricatedDirectConflictEvidence(
                    item.sentence(),
                    item.reason()
            )) {
                continue;
            }
            int currentCount = analysisCountByQuestionId.getOrDefault(question.getId(), 0);
            if (currentCount >= MAX_ANALYSES_PER_QUESTION) {
                continue;
            }
            String sentence = item.sentence();
            if (status != QuestionAnalysisStatus.PROVEN
                    && keyStrengthQuotes.contains(analysisResultSanitizationService.normalizeKeyword(sentence))) {
                continue;
            }
            String dedupeKey = question.getId() + ":" + sentence.trim();
            if (!seenSentences.add(dedupeKey)) {
                continue;
            }
            int start = findNextSentenceStart(
                    answer,
                    sentence,
                    nextSearchIndexByQuestionId.getOrDefault(question.getId(), 0)
            );
            if (start < 0) {
                continue;
            }
            if (status == QuestionAnalysisStatus.FABRICATED
                    && !fabricatedQuestionIds.add(question.getId())) {
                continue;
            }
            nextSearchIndexByQuestionId.put(question.getId(), start + sentence.length());
            analysisCountByQuestionId.put(question.getId(), currentCount + 1);

            result.add(QuestionAnalysis.create(
                    question,
                    analysis,
                    sentence,
                    defaultString(item.reason()),
                    normalizeImprovement(sentence, answer, item.improvement(), status),
                    status,
                    start,
                    start + sentence.length()
            ));
        }

        return result;
    }

    private Set<String> normalizedKeyStrengthQuotes(AnalysisLlmResponse llmResponse) {
        if (llmResponse == null || llmResponse.keyStrengths() == null) {
            return Set.of();
        }
        return llmResponse.keyStrengths().stream()
                .filter(item -> item != null && StringUtils.hasText(item.quote()))
                .map(item -> analysisResultSanitizationService.normalizeKeyword(item.quote()))
                .collect(Collectors.toSet());
    }

    private void validateRequiredScores(AnalysisLlmResponse llmResponse) {
        if (llmResponse == null
                || llmResponse.jobFit() == null
                || llmResponse.impact() == null
                || llmResponse.completeness() == null) {
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "자소서 분석 AI 응답에 필수 점수 필드가 누락되었습니다."
            );
        }
    }

    private int validateScore(String fieldName, Integer score) {
        if (score == null || score < MIN_SCORE || score > MAX_SCORE) {
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "자소서 분석 AI 응답의 " + fieldName + " 점수 범위가 올바르지 않습니다."
            );
        }
        return score;
    }

    private int calculateScore(int jobFit, int impact, int completeness) {
        return (int) Math.round(
                jobFit * JOB_FIT_WEIGHT
                        + impact * IMPACT_WEIGHT
                        + completeness * COMPLETENESS_WEIGHT
        );
    }

    private int findNextSentenceStart(String answer, String sentence, int fromIndex) {
        int start = answer.indexOf(sentence, Math.max(0, fromIndex));
        if (start >= 0) {
            return start;
        }
        return answer.indexOf(sentence);
    }

    private String normalizeFeedback(String feedback) {
        if (StringUtils.hasText(feedback)) {
            return feedback;
        }
        return "자소서 분석 결과를 확인해주세요.";
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String normalizeImprovement(
            String sentence,
            String answer,
            String improvement,
            QuestionAnalysisStatus status
    ) {
        return AnalysisSanitizationRules.normalizeImprovement(
                sentence,
                answer,
                improvement,
                status == QuestionAnalysisStatus.PROVEN
        );
    }

    private QuestionAnalysisStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }

        String normalizedStatus = status.trim().toUpperCase();
        if ("GOOD".equals(normalizedStatus)) {
            return QuestionAnalysisStatus.PROVEN;
        }
        if ("NEEDS_IMPROVEMENT".equals(normalizedStatus)) {
            return QuestionAnalysisStatus.MENTIONED;
        }
        if ("RISK".equals(normalizedStatus)) {
            return QuestionAnalysisStatus.FABRICATED;
        }

        try {
            return QuestionAnalysisStatus.valueOf(normalizedStatus);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
