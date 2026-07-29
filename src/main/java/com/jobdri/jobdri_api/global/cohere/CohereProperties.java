package com.jobdri.jobdri_api.global.cohere;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "cohere")
public record CohereProperties(
        String apiKey,
        String baseUrl,
        Embedding embedding
) {
    private static final String DEFAULT_BASE_URL = "https://api.cohere.com";

    public CohereProperties {
        baseUrl = hasText(baseUrl) ? baseUrl : DEFAULT_BASE_URL;
        embedding = embedding == null ? new Embedding(null, null, null, null) : embedding;
    }

    boolean hasApiKey() {
        return hasText(apiKey);
    }

    public record Embedding(
            String model,
            Integer dimension,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        private static final String DEFAULT_MODEL = "embed-v4.0";
        private static final int DEFAULT_DIMENSION = 1024;
        private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
        private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(15);

        public Embedding {
            model = hasText(model) ? model : DEFAULT_MODEL;
            dimension = dimension == null ? DEFAULT_DIMENSION : dimension;
            connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
            readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
