package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.jobdri.jobdri_api.domain.analysis.application.model.AnalysisExecutionPayload;
import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.service.sanitization.AnalysisSanitizationRules;
import com.jobdri.jobdri_api.domain.analysis.type.QuestionAnalysisStatus;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_SCORE;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MIN_SCORE;

@Service
@RequiredArgsConstructor
public class AnalysisResultValidationService {
    private final AnalysisInputFingerprintProvider analysisInputFingerprintProvider;

    public VerifiedAnswerSnapshot verifyAnswerSnapshot(
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

    public void validateRequiredScores(AnalysisLlmResponse llmResponse) {
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

    public int validateScore(String fieldName, Integer score) {
        if (score == null || score < MIN_SCORE || score > MAX_SCORE) {
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "자소서 분석 AI 응답의 " + fieldName + " 점수 범위가 올바르지 않습니다."
            );
        }
        return score;
    }

    public String normalizeFeedback(String feedback) {
        if (StringUtils.hasText(feedback)) {
            return feedback;
        }
        return "자소서 분석 결과를 확인해주세요.";
    }

    public String normalizeImprovement(
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

    public record VerifiedAnswerSnapshot(
            List<AnalysisExecutionPayload.AnswerSnapshot> answers,
            Map<Long, String> answerByQuestionId
    ) {
        public String combinedAnswers() {
            return answers.stream()
                    .map(AnalysisExecutionPayload.AnswerSnapshot::answer)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("\n"));
        }
    }
}
