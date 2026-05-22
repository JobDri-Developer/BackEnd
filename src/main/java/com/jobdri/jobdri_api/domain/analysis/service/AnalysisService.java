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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisService {

    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;
    private final AnalysisRepository analysisRepository;
    private final QuestionAnalysisRepository questionAnalysisRepository;
    private final AnalysisAiClient analysisAiClient;
    private final CreditService creditService;

    @Transactional
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
            replaceExistingAnalysis(mockApply);

            Analysis analysis = analysisRepository.save(Analysis.create(
                    mockApply,
                    clampScore(llmResponse.score()),
                    clampScore(llmResponse.jobFit()),
                    clampScore(llmResponse.impact()),
                    clampScore(llmResponse.completeness()),
                    normalizeFeedback(llmResponse.feedback())
            ));

            List<QuestionAnalysis> questionAnalyses = buildQuestionAnalyses(analysis, answeredQuestions, llmResponse);
            questionAnalysisRepository.saveAll(questionAnalyses);
            mockApply.updateStatus(MockApplyStatus.COMPLETED);

            return getAnalysis(user, mockApplyId);
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
                        "해당 모의 서류 지원의 분석 결과를 찾을 수 없습니다. mockApplyId=" + mockApplyId
                ));
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        List<QuestionAnalysis> questionAnalyses =
                questionAnalysisRepository.findAllByAnalysisIdOrderByQuestionIdAscIdAsc(analysis.getId());

        return toResponse(mockApply, analysis, questions, questionAnalyses);
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
            String sentence = item.sentence();
            int start = answer.indexOf(sentence);
            if (start < 0) {
                continue;
            }

            result.add(QuestionAnalysis.create(
                    question,
                    analysis,
                    sentence,
                    defaultString(item.reason()),
                    defaultString(item.improvement()),
                    normalizeStatus(item.status()),
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

        return AnalysisResponse.of(analysis, mockApply.getStatus(), questionResponses);
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

    private int clampScore(Integer score) {
        if (score == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, score));
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

    private QuestionAnalysisStatus normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return QuestionAnalysisStatus.MENTIONED;
        }

        try {
            return QuestionAnalysisStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return QuestionAnalysisStatus.MENTIONED;
        }
    }
}
