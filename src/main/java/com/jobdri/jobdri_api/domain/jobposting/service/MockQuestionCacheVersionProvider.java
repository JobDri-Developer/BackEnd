package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService;
import com.jobdri.jobdri_api.global.cohere.CohereProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class MockQuestionCacheVersionProvider {

    private static final String FINGERPRINT_SCHEMA_VERSION = "mock-question-cache-fingerprint-v1";
    private static final String POST_PROCESSING_VERSION = "trim-distinct-limit5-v1";
    private static final int FINGERPRINT_LENGTH = 12;

    private final MockQuestionCacheProperties mockQuestionCacheProperties;
    private final String extractionModel;
    private final String embeddingModel;
    private final int jdLimit;
    private final int questionLimit;

    public MockQuestionCacheVersionProvider(
            MockQuestionCacheProperties mockQuestionCacheProperties,
            CohereProperties cohereProperties,
            @Value("${openai.model.job-posting-extractor:gpt-4o-mini}") String extractionModel,
            @Value("${app.analysis.retrieval.jd-limit:3}") int jdLimit,
            @Value("${app.analysis.retrieval.question-limit:5}") int questionLimit
    ) {
        this.mockQuestionCacheProperties = mockQuestionCacheProperties;
        this.extractionModel = extractionModel;
        this.embeddingModel = cohereProperties.embedding().model();
        this.jdLimit = jdLimit;
        this.questionLimit = questionLimit;
    }

    public String currentVersion() {
        return versionPrefix() + ":" + automaticFingerprint();
    }

    private String versionPrefix() {
        String configuredPrefix = mockQuestionCacheProperties.getVersionPrefix();
        return StringUtils.hasText(configuredPrefix) ? configuredPrefix.trim() : "v1";
    }

    private String automaticFingerprint() {
        String source = String.join(
                "\n",
                FINGERPRINT_SCHEMA_VERSION,
                JobPostingAiService.MOCK_QUESTION_PROMPT_TEMPLATE,
                JobPostingAiService.REFERENCE_POSTING_TEMPLATE,
                JobPostingAiService.REFERENCE_QUESTION_TEMPLATE,
                JobPostingAiService.NO_REFERENCE_POSTING_TEXT,
                JobPostingAiService.NO_REFERENCE_QUESTION_TEXT,
                CorpusRetrievalService.MOCK_BASE_QUERY_TEMPLATE,
                "model=" + extractionModel,
                "temperature=" + JobPostingAiService.MOCK_QUESTION_TEMPERATURE,
                "maxReferenceFieldLength=" + JobPostingAiService.MAX_REFERENCE_FIELD_LENGTH,
                "embeddingModel=" + embeddingModel,
                "jdLimit=" + jdLimit,
                "questionLimit=" + questionLimit,
                "postProcessing=" + POST_PROCESSING_VERSION
        );
        return sha256Hex(source).substring(0, FINGERPRINT_LENGTH);
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
            throw new IllegalStateException("Mock question cache version fingerprint를 생성할 수 없습니다.", exception);
        }
    }
}
