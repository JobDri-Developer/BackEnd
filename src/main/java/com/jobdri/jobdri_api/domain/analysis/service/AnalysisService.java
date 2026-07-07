package com.jobdri.jobdri_api.domain.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisQuestionResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordSource;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
// 자소서 분석의 핵심 비즈니스 로직과 결과 저장을 담당하는 메인 서비스다.
public class AnalysisService {
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;
    private static final int MAX_ANALYSES_PER_QUESTION = 3;
    private static final int MAX_MISSING_KEYWORDS = 3;
    private static final int MAX_MISSING_KEYWORD_LENGTH = 60;
    private static final double JOB_FIT_WEIGHT = 0.50;
    private static final double IMPACT_WEIGHT = 0.30;
    private static final double COMPLETENESS_WEIGHT = 0.20;
    private static final TypeReference<List<MissingKeywordResponse>> MISSING_KEYWORDS_TYPE = new TypeReference<>() {
    };

    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;
    private final AnalysisRepository analysisRepository;
    private final QuestionAnalysisRepository questionAnalysisRepository;
    private final JobPostingService jobPostingService;
    private final AnalysisAiClient analysisAiClient;
    private final CreditService creditService;
    private final ObjectMapper objectMapper;

    @Transactional
    @AuditLogEvent(action = "ANALYSIS_RUN", targetType = "MOCK_APPLY", targetId = "#arg1")
    public AnalysisResponse analyze(User user, Long mockApplyId) {
        validateAnalysisRequest(user, mockApplyId);
        String referenceId = "mockApplyId=" + mockApplyId;
        deductAnalysisCredit(user, referenceId);

        try {
            AnalysisExecutionPayload payload = prepareAnalysisExecution(user, mockApplyId);
            AnalysisLlmResponse llmResponse = executeAnalysis(payload);
            AnalysisResponse response = finalizeAnalysis(user, mockApplyId, payload, llmResponse);
            return response;
        } catch (RuntimeException e) {
            refundAnalysisCredit(user, referenceId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public void validateAnalysisRequest(User user, Long mockApplyId) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        boolean hasAnsweredQuestion = questions.stream()
                .anyMatch(question -> StringUtils.hasText(question.getAnswer()));

        if (!hasAnsweredQuestion) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "분석할 자소서 답변이 1개 이상 필요합니다."
            );
        }
    }

    @Transactional
    public void deductAnalysisCredit(User user, String referenceId) {
        creditService.use(user, 1, "자소서 분석 크레딧 차감", referenceId);
    }

    @Transactional
    public void refundAnalysisCredit(User user, String referenceId) {
        creditService.refund(user, 1, "자소서 분석 크레딧 환불", referenceId);
    }

    @Transactional(readOnly = true)
    public AnalysisExecutionPayload prepareAnalysisExecution(User user, Long mockApplyId) {
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

        // Initialize hierarchy before leaving the read transaction so detached payload can be used safely.
        mockApply.getJobPosting().getDetailClassification().getMiddleClassification().getMiddleName();
        mockApply.getJobPosting().getDetailClassification().getMiddleClassification().getClassification().getBigName();

        return new AnalysisExecutionPayload(
                user.getId(),
                mockApplyId,
                mockApply.getJobPosting(),
                List.copyOf(questions),
                List.copyOf(answeredQuestions)
        );
    }

    public AnalysisLlmResponse executeAnalysis(AnalysisExecutionPayload payload) {
        return analysisAiClient.analyze(payload.jobPosting(), payload.answeredQuestions());
    }

    @Transactional
    public AnalysisResponse finalizeAnalysis(
            User user,
            Long mockApplyId,
            AnalysisExecutionPayload payload,
            AnalysisLlmResponse llmResponse
    ) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        validateRequiredScores(llmResponse);
        int jobFit = validateScore("jobFit", llmResponse.jobFit());
        int impact = validateScore("impact", llmResponse.impact());
        int completeness = validateScore("completeness", llmResponse.completeness());
        List<MissingKeywordResponse> missingKeywords = buildMissingKeywords(llmResponse);
        replaceExistingAnalysis(mockApply);

        Analysis analysis = analysisRepository.save(Analysis.create(
                mockApply,
                calculateScore(jobFit, impact, completeness),
                jobFit,
                impact,
                completeness,
                normalizeFeedback(llmResponse.feedback()),
                serializeMissingKeywords(missingKeywords)
        ));

        List<QuestionAnalysis> questionAnalyses = buildQuestionAnalyses(
                analysis,
                questions,
                payload.answeredQuestions(),
                llmResponse
        );
        questionAnalysisRepository.saveAll(questionAnalyses);
        mockApply.updateStatus(MockApplyStatus.COMPLETED);

        return toResponse(mockApply, analysis, questions, questionAnalyses, readMissingKeywords(analysis));
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

        return toResponse(
                mockApply,
                analysis,
                questions,
                questionAnalyses,
                readMissingKeywords(analysis)
        );
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

        return toResponse(
                mockApply,
                analysis,
                questions,
                questionAnalyses,
                readMissingKeywords(analysis)
        );
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
            List<Question> answeredQuestions,
            AnalysisLlmResponse llmResponse
    ) {
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, String> answerByQuestionId = answeredQuestions.stream()
                .collect(Collectors.toMap(Question::getId, Question::getAnswer));
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

            String answer = answerByQuestionId.get(item.questionId());
            if (!StringUtils.hasText(answer)) {
                continue;
            }
            QuestionAnalysisStatus status = parseStatus(item.status());
            if (status == null || status == QuestionAnalysisStatus.MISSING) {
                continue;
            }
            int currentCount = analysisCountByQuestionId.getOrDefault(question.getId(), 0);
            if (currentCount >= MAX_ANALYSES_PER_QUESTION) {
                continue;
            }
            String sentence = item.sentence();
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
            List<QuestionAnalysis> questionAnalyses,
            List<MissingKeywordResponse> missingKeywords
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
                missingKeywords,
                questionResponses
        );
    }

    private List<MissingKeywordResponse> buildMissingKeywords(AnalysisLlmResponse llmResponse) {
        if (llmResponse == null || llmResponse.missingKeywords() == null) {
            return List.of();
        }

        List<MissingKeywordResponse> result = new ArrayList<>();
        Set<String> seenKeywords = new HashSet<>();

        for (AnalysisLlmResponse.MissingKeywordItem item : llmResponse.missingKeywords()) {
            if (item == null || !StringUtils.hasText(item.keyword())) {
                continue;
            }

            String keyword = item.keyword().trim();
            if (keyword.length() > MAX_MISSING_KEYWORD_LENGTH) {
                continue;
            }

            Optional<MissingKeywordSource> source = MissingKeywordSource.from(item.source());
            if (source.isEmpty()) {
                continue;
            }

            String dedupeKey = normalizeKeyword(keyword);
            if (!seenKeywords.add(dedupeKey)) {
                continue;
            }

            result.add(new MissingKeywordResponse(keyword, source.get()));
            if (result.size() >= MAX_MISSING_KEYWORDS) {
                break;
            }
        }

        return result;
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.replaceAll("\\s+", "").toLowerCase();
    }

    private String serializeMissingKeywords(List<MissingKeywordResponse> missingKeywords) {
        try {
            return objectMapper.writeValueAsString(missingKeywords == null ? List.of() : missingKeywords);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize missingKeywords. Fallback to empty array.", e);
            return "[]";
        }
    }

    private List<MissingKeywordResponse> readMissingKeywords(Analysis analysis) {
        if (analysis == null || !StringUtils.hasText(analysis.getMissingKeywordsJson())) {
            return List.of();
        }

        try {
            List<MissingKeywordResponse> missingKeywords = objectMapper.readValue(
                    analysis.getMissingKeywordsJson(),
                    MISSING_KEYWORDS_TYPE
            );
            return sanitizeStoredMissingKeywords(missingKeywords);
        } catch (Exception e) {
            log.warn(
                    "Failed to deserialize missingKeywords. analysisId={}, fallback to empty array.",
                    analysis.getId(),
                    e
            );
            return List.of();
        }
    }

    private List<MissingKeywordResponse> sanitizeStoredMissingKeywords(List<MissingKeywordResponse> missingKeywords) {
        if (missingKeywords == null) {
            return List.of();
        }

        List<MissingKeywordResponse> result = new ArrayList<>();
        Set<String> seenKeywords = new HashSet<>();

        for (MissingKeywordResponse item : missingKeywords) {
            if (item == null || !StringUtils.hasText(item.keyword()) || item.source() == null) {
                continue;
            }

            String keyword = item.keyword().trim();
            if (keyword.length() > MAX_MISSING_KEYWORD_LENGTH) {
                continue;
            }

            String dedupeKey = normalizeKeyword(keyword);
            if (!seenKeywords.add(dedupeKey)) {
                continue;
            }

            result.add(new MissingKeywordResponse(keyword, item.source()));
            if (result.size() >= MAX_MISSING_KEYWORDS) {
                break;
            }
        }

        return result;
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
        return AnalysisImprovementRules.isInstructionLike(improvement);
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
