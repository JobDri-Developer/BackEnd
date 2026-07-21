package com.jobdri.jobdri_api.domain.analysis.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordSource;
import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysisStatus;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisAiClient;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisPromptInput;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisResultConstants;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisSanitizationRules;
import com.jobdri.jobdri_api.domain.analysis.service.JobCategoryEvaluationCriteriaProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationAnalysisBatchService {
    private static final Long EVALUATION_QUESTION_ID = 1L;
    private static final List<String> REQUIRED_HEADERS = List.of(
            "caseId",
            "jobCategoryMiddle",
            "jobCategorySmall",
            "mainTasks",
            "qualifications",
            "preferences",
            "question",
            "answer"
    );

    private final AnalysisAiClient analysisAiClient;
    private final JobCategoryEvaluationCriteriaProvider jobCategoryEvaluationCriteriaProvider;
    private final ObjectMapper objectMapper;

    public EvaluationBatchSummary run(Path inputPath, Path outputPath) throws IOException {
        List<EvaluationAnalysisCase> cases = readCases(inputPath);
        List<EvaluationAnalysisResult> results = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < cases.size(); i++) {
            EvaluationAnalysisCase evaluationCase = cases.get(i);
            log.info("[{}/{}] {} analyzing...", i + 1, cases.size(), evaluationCase.caseId());

            try {
                EvaluationAnalysisResult result = analyzeCase(evaluationCase);
                results.add(result);
                if (!StringUtils.hasText(result.errorMessage())) {
                    successCount++;
                }
            } catch (Exception e) {
                log.warn("[{}/{}] {} failed. message={}", i + 1, cases.size(), evaluationCase.caseId(), e.getMessage());
                results.add(EvaluationAnalysisResult.failed(
                        evaluationCase,
                        safeErrorMessage(e),
                        createdAt()
                ));
            }
        }

        EvaluationCsvSupport.write(outputPath, results);
        return new EvaluationBatchSummary(cases.size(), successCount, cases.size() - successCount, outputPath);
    }

    private List<EvaluationAnalysisCase> readCases(Path inputPath) throws IOException {
        validateHeaders(EvaluationCsvSupport.readHeaders(inputPath));
        return EvaluationCsvSupport.read(inputPath).stream()
                .map(row -> new EvaluationAnalysisCase(
                        value(row, "caseId"),
                        value(row, "jobCategoryMiddle"),
                        value(row, "jobCategorySmall"),
                        value(row, "mainTasks"),
                        value(row, "qualifications"),
                        value(row, "preferences"),
                        value(row, "question"),
                        value(row, "answer")
                ))
                .toList();
    }

    private void validateHeaders(List<String> headers) {
        Set<String> headerSet = new HashSet<>(headers);
        List<String> missingHeaders = REQUIRED_HEADERS.stream()
                .filter(header -> !headerSet.contains(header))
                .toList();
        if (!missingHeaders.isEmpty()) {
            throw new IllegalArgumentException("Evaluation CSV missing required headers: " + missingHeaders);
        }
    }

    private EvaluationAnalysisResult analyzeCase(EvaluationAnalysisCase evaluationCase) {
        AnalysisPromptInput promptInput = new AnalysisPromptInput(
                "평가용 회사",
                evaluationCase.jobCategorySmall(),
                evaluationCase.mainTasks(),
                evaluationCase.qualifications(),
                evaluationCase.preferences(),
                List.of(new AnalysisPromptInput.QuestionAnswer(
                        EVALUATION_QUESTION_ID,
                        evaluationCase.question(),
                        evaluationCase.answer()
                ))
        );

        AnalysisLlmResponse llmResponse = analysisAiClient.analyzeForEvaluation(
                promptInput,
                jobCategoryEvaluationCriteriaProvider
                        .findByMiddleName(evaluationCase.jobCategoryMiddle())
                        .orElse(null)
        );

        int jobFit = validateScore("jobFit", llmResponse == null ? null : llmResponse.jobFit());
        int impact = validateScore("impact", llmResponse == null ? null : llmResponse.impact());
        int completeness = validateScore("completeness", llmResponse == null ? null : llmResponse.completeness());
        List<MissingKeywordResponse> missingKeywords = buildMissingKeywords(evaluationCase, llmResponse);
        List<EvaluationQuestionAnalysisResult> questionAnalyses = buildQuestionAnalyses(evaluationCase, llmResponse);

        return new EvaluationAnalysisResult(
                evaluationCase.caseId(),
                evaluationCase.jobCategoryMiddle(),
                evaluationCase.jobCategorySmall(),
                calculateScore(jobFit, impact, completeness),
                jobFit,
                impact,
                completeness,
                normalizeFeedback(llmResponse.feedback()),
                writeJson(missingKeywords),
                writeJson(questionAnalyses),
                writeJson(llmResponse),
                "",
                createdAt()
        );
    }

    private List<MissingKeywordResponse> buildMissingKeywords(
            EvaluationAnalysisCase evaluationCase,
            AnalysisLlmResponse llmResponse
    ) {
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
            if (keyword.length() > AnalysisResultConstants.MAX_MISSING_KEYWORD_LENGTH) {
                continue;
            }

            Optional<MissingKeywordSource> source = MissingKeywordSource.from(item.source());
            if (source.isEmpty()) {
                continue;
            }
            if (!AnalysisSanitizationRules.isValidMissingKeyword(
                    keyword,
                    source.get(),
                    evaluationCase.mainTasks(),
                    evaluationCase.qualifications()
            )) {
                continue;
            }

            String dedupeKey = normalizeKeyword(keyword);
            if (!seenKeywords.add(dedupeKey)) {
                continue;
            }

            result.add(new MissingKeywordResponse(keyword, source.get()));
            if (result.size() >= AnalysisResultConstants.MAX_MISSING_KEYWORDS) {
                break;
            }
        }
        return result;
    }

    private List<EvaluationQuestionAnalysisResult> buildQuestionAnalyses(
            EvaluationAnalysisCase evaluationCase,
            AnalysisLlmResponse llmResponse
    ) {
        if (llmResponse == null || llmResponse.questionAnalyses() == null) {
            return List.of();
        }

        String answer = evaluationCase.answer();
        if (!StringUtils.hasText(answer)) {
            return List.of();
        }

        List<EvaluationQuestionAnalysisResult> result = new ArrayList<>();
        Map<Long, Integer> analysisCountByQuestionId = new HashMap<>();
        Map<Long, Integer> nextSearchIndexByQuestionId = new HashMap<>();
        Set<String> seenSentences = new HashSet<>();

        for (AnalysisLlmResponse.QuestionAnalysisItem item : llmResponse.questionAnalyses()) {
            if (item == null || item.questionId() == null || !StringUtils.hasText(item.sentence())) {
                continue;
            }
            if (!EVALUATION_QUESTION_ID.equals(item.questionId())) {
                continue;
            }

            QuestionAnalysisStatus status = parseStatus(item.status());
            if (status == null || status == QuestionAnalysisStatus.MISSING) {
                continue;
            }
            if (status == QuestionAnalysisStatus.PROVEN
                    && AnalysisSanitizationRules.isContradictoryProvenReason(item.reason())) {
                continue;
            }

            int currentCount = analysisCountByQuestionId.getOrDefault(item.questionId(), 0);
            if (currentCount >= AnalysisResultConstants.MAX_ANALYSES_PER_QUESTION) {
                continue;
            }

            String sentence = item.sentence();
            String dedupeKey = item.questionId() + ":" + sentence.trim();
            if (!seenSentences.add(dedupeKey)) {
                continue;
            }

            int start = findNextSentenceStart(
                    answer,
                    sentence,
                    nextSearchIndexByQuestionId.getOrDefault(item.questionId(), 0)
            );
            if (start < 0) {
                continue;
            }

            nextSearchIndexByQuestionId.put(item.questionId(), start + sentence.length());
            analysisCountByQuestionId.put(item.questionId(), currentCount + 1);
            result.add(new EvaluationQuestionAnalysisResult(
                    item.questionId(),
                    sentence,
                    status.name().toLowerCase(),
                    defaultString(item.reason()),
                    normalizeImprovement(sentence, answer, item.improvement(), status),
                    start,
                    start + sentence.length()
            ));
        }
        return result;
    }

    private int validateScore(String fieldName, Integer score) {
        if (score == null
                || score < AnalysisResultConstants.MIN_SCORE
                || score > AnalysisResultConstants.MAX_SCORE) {
            throw new IllegalArgumentException("자소서 분석 AI 응답의 " + fieldName + " 점수 범위가 올바르지 않습니다.");
        }
        return score;
    }

    private int calculateScore(int jobFit, int impact, int completeness) {
        return (int) Math.round(
                jobFit * AnalysisResultConstants.JOB_FIT_WEIGHT
                        + impact * AnalysisResultConstants.IMPACT_WEIGHT
                        + completeness * AnalysisResultConstants.COMPLETENESS_WEIGHT
        );
    }

    private int findNextSentenceStart(String answer, String sentence, int fromIndex) {
        int start = answer.indexOf(sentence, Math.max(0, fromIndex));
        if (start >= 0) {
            return start;
        }
        return answer.indexOf(sentence);
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException e) {
            log.warn("평가 결과 JSON 직렬화에 실패했습니다. 빈 배열로 대체합니다.", e);
            return "[]";
        }
    }

    private String normalizeFeedback(String feedback) {
        if (StringUtils.hasText(feedback)) {
            return feedback;
        }
        return "자소서 분석 결과를 확인해주세요.";
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

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.replaceAll("\\s+", "").toLowerCase();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String value(Map<String, String> row, String key) {
        return row.getOrDefault(key, "");
    }

    private String safeErrorMessage(Exception e) {
        String message = e.getMessage();
        return StringUtils.hasText(message) ? message : e.getClass().getSimpleName();
    }

    private String createdAt() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public record EvaluationBatchSummary(
            int totalCount,
            int successCount,
            int failureCount,
            Path outputPath
    ) {
    }
}
