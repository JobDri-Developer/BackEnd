package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisQuestionResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionAnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.Analysis;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysis;
import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysisStatus;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionAnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingService;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.payment.service.CreditService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisService {
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;
    private static final int MAX_ANALYSES_PER_QUESTION = 3;
    private static final double JOB_FIT_WEIGHT = 0.40;
    private static final double IMPACT_WEIGHT = 0.35;
    private static final double COMPLETENESS_WEIGHT = 0.25;

    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;
    private final AnalysisRepository analysisRepository;
    private final QuestionAnalysisRepository questionAnalysisRepository;
    private final JobPostingService jobPostingService;
    private final AnalysisAiClient analysisAiClient;
    private final CreditService creditService;

    @Transactional
    @AuditLogEvent(action = "ANALYSIS_RUN", targetType = "MOCK_APPLY", targetId = "#arg1")
    public AnalysisResponse analyze(User user, Long mockApplyId) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        List<Question> answeredQuestions = questions.stream()
                .filter(question -> StringUtils.hasText(question.getAnswer()))
                .toList();

        if (answeredQuestions.isEmpty()) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "분석할 자소서 답변이 1개 이상 필요합니다."
            );
        }

        String referenceId = "mockApplyId=" + mockApply.getId();
        creditService.use(user, 1, "자소서 분석 크레딧 차감", referenceId);

        try {
            AnalysisLlmResponse llmResponse = analysisAiClient.analyze(mockApply.getJobPosting(), answeredQuestions);
            validateRequiredScores(llmResponse);
            int jobFit = validateScore("jobFit", llmResponse.jobFit());
            int impact = validateScore("impact", llmResponse.impact());
            int completeness = validateScore("completeness", llmResponse.completeness());
            int score = calculateScore(jobFit, impact, completeness);
            replaceExistingAnalysis(mockApply);

            Analysis analysis = analysisRepository.save(Analysis.create(
                    mockApply,
                    score,
                    jobFit,
                    impact,
                    completeness,
                    normalizeFeedback(llmResponse.feedback())
            ));

            List<QuestionAnalysis> questionAnalyses = buildQuestionAnalyses(analysis, answeredQuestions, llmResponse);
            questionAnalysisRepository.saveAll(questionAnalyses);
            mockApply.updateStatus(MockApplyStatus.COMPLETED);

            return toResponse(mockApply, analysis, questions, questionAnalyses);
        } catch (RuntimeException e) {
            creditService.refund(user, 1, "자소서 분석 실패 환불", referenceId);
            throw e;
        }
    }

    public AnalysisResponse getAnalysis(User user, Long mockApplyId) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        Analysis analysis = analysisRepository.findByMockApplyId(mockApply.getId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.ANALYSIS_NOT_FOUND,
                        "해당 모의 서류 지원의 분석 결과를 찾을 수 없습니다. mockApplyId=" + mockApply.getId()
                ));
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        List<QuestionAnalysis> questionAnalyses =
                questionAnalysisRepository.findAllByAnalysisIdOrderByQuestionIdAscIdAsc(analysis.getId());

        return toResponse(mockApply, analysis, questions, questionAnalyses);
    }

    public AnalysisResponse getAnalysisByJobPostingSequence(User user, Long jobPostingId, int sequence) {
        JobPosting jobPosting = jobPostingService.getOwnedJobPosting(user, jobPostingId);
        MockApply mockApply = resolveMockApplyBySequence(jobPosting, sequence);
        Analysis analysis = analysisRepository.findByMockApplyId(mockApply.getId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.ANALYSIS_NOT_FOUND,
                        "해당 모의 서류 지원의 분석 결과를 찾을 수 없습니다. mockApplyId=" + mockApply.getId()
                ));
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        List<QuestionAnalysis> questionAnalyses =
                questionAnalysisRepository.findAllByAnalysisIdOrderByQuestionIdAscIdAsc(analysis.getId());

        return toResponse(mockApply, analysis, questions, questionAnalyses);
    }

    private MockApply resolveMockApplyBySequence(JobPosting jobPosting, int sequence) {
        if (sequence < 1) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "sequence는 1 이상의 값이어야 합니다."
            );
        }

        List<MockApply> mockApplies = mockApplyRepository.findAllByUserIdAndJobPostingIdOrderByIdAsc(
                jobPosting.getUser().getId(),
                jobPosting.getId()
        );

        int derivedSequence = 0;
        for (MockApply mockApply : mockApplies) {
            derivedSequence++;
            int resolvedSequence = mockApply.getSequence() != null && mockApply.getSequence() > 0
                    ? mockApply.getSequence()
                    : derivedSequence;

            if (resolvedSequence == sequence) {
                return mockApply;
            }
        }

        throw new GeneralException(
                GeneralErrorCode.MOCK_APPLY_NOT_FOUND,
                "해당 순번의 모의 서류 지원을 찾을 수 없습니다. jobPostingId="
                        + jobPosting.getId()
                        + ", sequence="
                        + sequence
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

    private List<QuestionAnalysis> buildQuestionAnalyses(
            Analysis analysis,
            List<Question> questions,
            AnalysisLlmResponse llmResponse
    ) {
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        List<QuestionAnalysis> result = new ArrayList<>();
        Map<Long, Integer> analysisCountByQuestionId = new HashMap<>();
        Map<Long, Integer> nextSearchIndexByQuestionId = new HashMap<>();
        Set<String> seenSentences = new HashSet<>();

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

            String answer = question.getAnswer();
            String sentence = item.sentence().trim();
            if (!StringUtils.hasText(answer) || !StringUtils.hasText(sentence)) {
                continue;
            }

            QuestionAnalysisStatus status = parseStatus(item.status());
            if (status == null || status == QuestionAnalysisStatus.MISSING) {
                continue;
            }

            String dedupeKey = question.getId() + "\n" + sentence;
            if (seenSentences.contains(dedupeKey)) {
                continue;
            }

            int currentCount = analysisCountByQuestionId.getOrDefault(question.getId(), 0);
            if (currentCount >= MAX_ANALYSES_PER_QUESTION) {
                continue;
            }

            int start = findNextSentenceStart(answer, sentence, nextSearchIndexByQuestionId.getOrDefault(question.getId(), 0));
            if (start < 0) {
                continue;
            }
            seenSentences.add(dedupeKey);
            nextSearchIndexByQuestionId.put(question.getId(), start + sentence.length());
            analysisCountByQuestionId.put(question.getId(), currentCount + 1);

            result.add(QuestionAnalysis.create(
                    question,
                    analysis,
                    sentence,
                    defaultString(item.reason()),
                    normalizeImprovement(item.improvement()),
                    status,
                    start,
                    start + sentence.length()
            ));
        }

        return result;
    }

    private AnalysisResponse toResponse(
            MockApply mockApply,
            Analysis analysis,
            List<Question> questions,
            List<QuestionAnalysis> questionAnalyses
    ) {
        Map<Long, List<QuestionAnalysisResponse>> analysesByQuestionId = questionAnalyses.stream()
                .collect(Collectors.groupingBy(
                        questionAnalysis -> questionAnalysis.getQuestion().getId(),
                        Collectors.mapping(QuestionAnalysisResponse::from, Collectors.toList())
                ));

        List<AnalysisQuestionResponse> questionResponses = questions.stream()
                .sorted(Comparator.comparing(Question::getId))
                .map(question -> AnalysisQuestionResponse.of(
                        question,
                        analysesByQuestionId.getOrDefault(question.getId(), List.of())
                ))
                .toList();

        return AnalysisResponse.of(
                analysis,
                mockApply.getStatus(),
                mockApplyRepository.calculateSequence(mockApply),
                questionResponses
        );
    }

    private MockApply getOwnedMockApply(User user, Long mockApplyId) {
        MockApply mockApply = mockApplyRepository.findById(mockApplyId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.MOCK_APPLY_NOT_FOUND,
                        "해당 모의 서류 지원을 찾을 수 없습니다. mockApplyId=" + mockApplyId
                ));

        if (!mockApply.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 모의 서류 지원에 접근할 수 없습니다.");
        }

        return mockApply;
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

    private String normalizeImprovement(String improvement) {
        if (!StringUtils.hasText(improvement)) {
            return "";
        }

        String normalized = improvement.trim();
        if (isInstructionLikeImprovement(normalized)) {
            return "";
        }
        return normalized;
    }

    private boolean isInstructionLikeImprovement(String improvement) {
        return improvement.matches(".*(추가|보완|수정|작성)\\s*(하|해)\\s*(세요|주세요|주십시오|십시오).*")
                || improvement.matches(".*(해주세요|해 주세요|하십시오|해주십시오|해 주십시오).*")
                || improvement.matches(".*(하세요|십시오)\\.?$")
                || improvement.contains("필요합니다")
                || improvement.contains("해야 합니다")
                || improvement.contains("명확히 해야")
                || improvement.contains("명확히 하세요");
    }

    private QuestionAnalysisStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }

        try {
            return QuestionAnalysisStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
