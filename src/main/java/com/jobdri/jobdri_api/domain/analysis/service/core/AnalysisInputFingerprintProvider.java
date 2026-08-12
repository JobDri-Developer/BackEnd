package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.application.model.AnalysisExecutionPayload;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.CorpusReferenceContext;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.SimilarJobPostingContext;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.analysis.service.ai.FewShotPromptProvider;
import com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot.FewShotProperties;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.global.cohere.CohereProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AnalysisInputFingerprintProvider {

    private static final String FINGERPRINT_SCHEMA_VERSION = "analysis-input-fingerprint-v3";
    private static final String ANALYSIS_PROMPT_POLICY_VERSION = "analysis-prompt-policy-v3-curated-corpus-rag";
    private static final double ANALYSIS_TEMPERATURE = 0.2;

    private final ObjectMapper objectMapper;
    private final FewShotPromptProvider fewShotPromptProvider;
    private final FewShotProperties fewShotProperties;
    private final String analysisModel;
    private final boolean twoPassEnabled;
    private final String analysisMode;
    private final String embeddingModel;
    private final int jdLimit;
    private final int questionLimit;

    public AnalysisInputFingerprintProvider(
            ObjectMapper objectMapper,
            FewShotPromptProvider fewShotPromptProvider,
            FewShotProperties fewShotProperties,
            CohereProperties cohereProperties,
            @Value("${openai.model.cover-letter-analysis:gpt-4o-mini}") String analysisModel,
            @Value("${analysis.two-pass.enabled:false}") boolean twoPassEnabled,
            @Value("${analysis.mode:}") String analysisMode,
            @Value("${app.analysis.retrieval.jd-limit:3}") int jdLimit,
            @Value("${app.analysis.retrieval.question-limit:5}") int questionLimit
    ) {
        this.objectMapper = objectMapper;
        this.fewShotPromptProvider = fewShotPromptProvider;
        this.fewShotProperties = fewShotProperties;
        this.analysisModel = analysisModel;
        this.twoPassEnabled = twoPassEnabled;
        this.analysisMode = analysisMode;
        this.embeddingModel = cohereProperties.embedding().model();
        this.jdLimit = jdLimit;
        this.questionLimit = questionLimit;
    }

    public String create(AnalysisExecutionPayload payload) {
        Map<String, Object> fingerprintSource = new LinkedHashMap<>();
        fingerprintSource.put("schemaVersion", FINGERPRINT_SCHEMA_VERSION);
        fingerprintSource.put("promptPolicyVersion", ANALYSIS_PROMPT_POLICY_VERSION);
        fingerprintSource.put("analysisMode", resolveAnalysisMode());
        fingerprintSource.put("analysisModel", analysisModel);
        fingerprintSource.put("temperature", ANALYSIS_TEMPERATURE);
        fingerprintSource.put("fewShotPrompt", fewShotPromptProvider.getPrompt());
        fingerprintSource.put("fewShotPolicy", fewShotPolicy());
        fingerprintSource.put("retrievalPolicy", retrievalPolicy());
        fingerprintSource.put("retrievalContext", retrievalContextFingerprintSource(payload.retrievalContext()));
        fingerprintSource.put("similarJobPostings", similarJobPostingFingerprintSource(payload.similarJobPostings()));
        fingerprintSource.put("jobPosting", jobPostingFingerprintSource(payload.jobPosting()));
        fingerprintSource.put("answeredQuestions", answeredQuestionFingerprintSource(payload.answeredQuestions()));
        fingerprintSource.put("jobCategoryEvaluationCriteria", payload.jobCategoryEvaluationCriteria());
        return sha256Hex(writeFingerprintSource(fingerprintSource));
    }

    public String createAnswerFingerprint(List<AnalysisExecutionPayload.AnswerSnapshot> answerSnapshots) {
        List<AnalysisExecutionPayload.AnswerSnapshot> orderedSnapshots = answerSnapshots == null
                ? List.of()
                : answerSnapshots.stream()
                        .sorted(Comparator.comparing(AnalysisExecutionPayload.AnswerSnapshot::questionId))
                        .toList();
        Map<String, Object> fingerprintSource = new LinkedHashMap<>();
        fingerprintSource.put("schemaVersion", "analysis-answer-fingerprint-v1");
        fingerprintSource.put("answers", orderedSnapshots);
        return sha256Hex(writeFingerprintSource(fingerprintSource));
    }

    public String createAnswerFingerprintFromQuestions(List<Question> questions) {
        return createAnswerFingerprint(AnalysisExecutionPayload.snapshotAnswers(questions));
    }

    private Map<String, Object> retrievalPolicy() {
        Map<String, Object> retrievalPolicy = new LinkedHashMap<>();
        retrievalPolicy.put("embeddingModel", embeddingModel);
        retrievalPolicy.put("jdLimit", jdLimit);
        retrievalPolicy.put("questionLimit", questionLimit);
        retrievalPolicy.put("jobPostingQueryTemplate", CorpusRetrievalService.ANALYSIS_JOB_POSTING_QUERY_TEMPLATE);
        retrievalPolicy.put("questionQueryTemplate", CorpusRetrievalService.ANALYSIS_QUESTION_QUERY_TEMPLATE);
        return retrievalPolicy;
    }

    private Map<String, Object> fewShotPolicy() {
        Map<String, Object> fewShotPolicy = new LinkedHashMap<>();
        fewShotPolicy.put("dynamicSelectionEnabled", fewShotProperties.isDynamicSelectionEnabled());
        fewShotPolicy.put("datasetVersion", fewShotProperties.getDatasetVersion());
        fewShotPolicy.put("topK", fewShotProperties.getSearch().getTopK());
        fewShotPolicy.put("candidateLimit", fewShotProperties.getSearch().getCandidateLimit());
        fewShotPolicy.put("reviewedEvaluationEnabled", fewShotProperties.getSource().isReviewedEvaluationEnabled());
        return fewShotPolicy;
    }

    private Map<String, Object> retrievalContextFingerprintSource(RetrievalContext retrievalContext) {
        Map<String, Object> retrievalContextSource = new LinkedHashMap<>();
        retrievalContextSource.put(
                "corpusReferences",
                CorpusReferenceContext.from(retrievalContext).stream()
                        .map(reference -> {
                            Map<String, Object> source = new LinkedHashMap<>();
                            source.put("corpusId", reference.corpusId());
                            source.put("category", reference.category());
                            source.put("title", reference.title());
                            source.put("content", reference.content());
                            source.put("rank", reference.rank());
                            return source;
                        })
                        .toList()
        );
        return retrievalContextSource;
    }

    private Map<String, Object> jobPostingFingerprintSource(JobPosting jobPosting) {
        Map<String, Object> jobPostingSource = new LinkedHashMap<>();
        jobPostingSource.put("companyName", defaultString(jobPosting.getCompany().getName()));
        jobPostingSource.put("bigClassification", defaultString(
                jobPosting.getDetailClassification().getMiddleClassification().getClassification().getBigName()
        ));
        jobPostingSource.put("middleClassification", defaultString(
                jobPosting.getDetailClassification().getMiddleClassification().getMiddleName()
        ));
        jobPostingSource.put("detailClassification", defaultString(jobPosting.getDetailClassification().getDetailName()));
        jobPostingSource.put("postingName", defaultString(jobPosting.getPostingName()));
        jobPostingSource.put("jobTitle", defaultString(jobPosting.getJobTitle()));
        jobPostingSource.put("task", defaultString(jobPosting.getTask()));
        jobPostingSource.put("requirement", defaultString(jobPosting.getRequirement()));
        jobPostingSource.put("preferred", defaultString(jobPosting.getPreferred()));
        return jobPostingSource;
    }

    private List<Map<String, Object>> similarJobPostingFingerprintSource(
            List<SimilarJobPostingContext> similarJobPostings
    ) {
        if (similarJobPostings == null) {
            return List.of();
        }
        return similarJobPostings.stream()
                .map(context -> {
                    Map<String, Object> source = new LinkedHashMap<>();
                    source.put("jobPostingId", context.jobPostingId());
                    source.put("companyName", defaultString(context.companyName()));
                    source.put("postingName", defaultString(context.postingName()));
                    source.put("jobTitle", defaultString(context.jobTitle()));
                    source.put("task", defaultString(context.task()));
                    source.put("requirements", defaultString(context.requirements()));
                    source.put("preferredQualifications", defaultString(context.preferredQualifications()));
                    source.put("similarityRank", context.similarityRank());
                    return source;
                })
                .toList();
    }

    private List<Map<String, Object>> answeredQuestionFingerprintSource(List<Question> answeredQuestions) {
        return answeredQuestions.stream()
                .map(question -> {
                    Map<String, Object> questionSource = new LinkedHashMap<>();
                    questionSource.put("questionId", question.getId());
                    questionSource.put("content", defaultString(question.getContent()));
                    questionSource.put("answer", defaultString(question.getAnswer()));
                    questionSource.put("limit", question.getLimit());
                    return questionSource;
                })
                .toList();
    }

    private String resolveAnalysisMode() {
        if (StringUtils.hasText(analysisMode)) {
            return analysisMode.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        }
        return twoPassEnabled ? "TWO_PASS" : "SINGLE_PASS";
    }

    private String writeFingerprintSource(Map<String, Object> fingerprintSource) {
        try {
            return objectMapper.writeValueAsString(fingerprintSource);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("analysis input fingerprint source를 직렬화할 수 없습니다.", exception);
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("analysis input fingerprint를 생성할 수 없습니다.", exception);
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
