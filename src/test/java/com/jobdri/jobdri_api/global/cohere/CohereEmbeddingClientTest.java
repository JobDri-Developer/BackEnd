package com.jobdri.jobdri_api.global.cohere;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CohereEmbeddingClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("embedDocuments는 search_document 요청을 보내고 float embeddings를 반환한다")
    void embedDocuments() throws Exception {
        AtomicReference<JsonNode> requestJson = new AtomicReference<>();
        try (TestCohereServer server = startServer(200, responseJson(3, 2), requestJson)) {
            CohereEmbeddingClient client = client(server.baseUrl(), "test-api-key", 3);

            List<float[]> embeddings = client.embedDocuments(List.of(
                    "Spring Boot 기반 REST API 개발",
                    "PostgreSQL 성능 최적화"
            ));

            assertThat(embeddings).hasSize(2);
            assertThat(embeddings.getFirst()).hasSize(3);
            assertThat(embeddings.getFirst()[0]).isEqualTo(0.1f);
            assertThat(requestJson.get().get("model").asText()).isEqualTo("embed-v4.0");
            assertThat(requestJson.get().get("input_type").asText()).isEqualTo("search_document");
            assertThat(requestJson.get().get("output_dimension").asInt()).isEqualTo(3);
            assertThat(requestJson.get().get("embedding_types").get(0).asText()).isEqualTo("float");
            assertThat(requestJson.get().get("texts")).hasSize(2);
            assertThat(server.authorizationHeader()).isEqualTo("Bearer test-api-key");
        }
    }

    @Test
    @DisplayName("embedQuery는 search_query 요청을 보내고 단일 embedding을 반환한다")
    void embedQuery() throws Exception {
        AtomicReference<JsonNode> requestJson = new AtomicReference<>();
        try (TestCohereServer server = startServer(200, responseJson(3, 1), requestJson)) {
            CohereEmbeddingClient client = client(server.baseUrl(), "test-api-key", 3);

            float[] embedding = client.embedQuery("Spring Boot 기반 REST API 개발 및 PostgreSQL 성능 최적화");

            assertThat(embedding).hasSize(3);
            assertThat(requestJson.get().get("input_type").asText()).isEqualTo("search_query");
            assertThat(requestJson.get().get("texts")).hasSize(1);
        }
    }

    @Test
    @DisplayName("API Key가 없으면 호출 시 명확한 예외를 발생시킨다")
    void missingApiKey() {
        CohereEmbeddingClient client = client("http://localhost:1", "", 3);

        assertThatThrownBy(() -> client.embedDocuments(List.of("text")))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE))
                .hasMessageContaining("API 키");
    }

    @Test
    @DisplayName("빈 입력과 blank query는 API 호출 전에 입력 오류로 처리한다")
    void invalidInput() {
        CohereEmbeddingClient client = client("http://localhost:1", "test-api-key", 3);

        assertThatThrownBy(() -> client.embedDocuments(List.of()))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.INVALID_PARAMETER));
        assertThatThrownBy(() -> client.embedDocuments(List.of("valid", " ")))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.INVALID_PARAMETER));
        assertThatThrownBy(() -> client.embedQuery(" "))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.INVALID_PARAMETER));
    }

    @Test
    @DisplayName("96개 초과 입력은 API 호출 전에 입력 오류로 처리한다")
    void tooManyTexts() {
        CohereEmbeddingClient client = client("http://localhost:1", "test-api-key", 3);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < 97; i++) {
            texts.add("text-" + i);
        }

        assertThatThrownBy(() -> client.embedDocuments(texts))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.INVALID_PARAMETER))
                .hasMessageContaining("96");
    }

    @Test
    @DisplayName("Cohere 429와 5xx는 서비스 일시 장애 예외로 변환한다")
    void transientCohereErrors() throws Exception {
        for (int status : List.of(429, 500)) {
            try (TestCohereServer server = startServer(status, "{\"message\":\"temporary\"}", new AtomicReference<>())) {
                CohereEmbeddingClient client = client(server.baseUrl(), "test-api-key", 3);

                assertThatThrownBy(() -> client.embedDocuments(List.of("text")))
                        .isInstanceOfSatisfying(GeneralException.class, exception ->
                                assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE));
            }
        }
    }

    @Test
    @DisplayName("Cohere 400, 401, 403은 요청 또는 설정 오류로 변환한다")
    void requestOrConfigurationErrors() throws Exception {
        for (int status : List.of(400, 401, 403)) {
            try (TestCohereServer server = startServer(status, "{\"message\":\"bad request\"}", new AtomicReference<>())) {
                CohereEmbeddingClient client = client(server.baseUrl(), "test-api-key", 3);

                assertThatThrownBy(() -> client.embedDocuments(List.of("text")))
                        .isInstanceOfSatisfying(GeneralException.class, exception ->
                                assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.INVALID_PARAMETER));
            }
        }
    }

    @Test
    @DisplayName("응답 embedding 개수가 요청 texts 개수와 다르면 예외 처리한다")
    void mismatchedEmbeddingCount() throws Exception {
        try (TestCohereServer server = startServer(200, responseJson(3, 1), new AtomicReference<>())) {
            CohereEmbeddingClient client = client(server.baseUrl(), "test-api-key", 3);

            assertThatThrownBy(() -> client.embedDocuments(List.of("first", "second")))
                    .isInstanceOfSatisfying(GeneralException.class, exception ->
                            assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE))
                    .hasMessageContaining("개수");
        }
    }

    @Test
    @DisplayName("응답 embedding 차원이 설정 차원과 다르면 예외 처리한다")
    void mismatchedEmbeddingDimension() throws Exception {
        try (TestCohereServer server = startServer(200, responseJson(2, 1), new AtomicReference<>())) {
            CohereEmbeddingClient client = client(server.baseUrl(), "test-api-key", 3);

            assertThatThrownBy(() -> client.embedQuery("query"))
                    .isInstanceOfSatisfying(GeneralException.class, exception ->
                            assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE))
                    .hasMessageContaining("차원");
        }
    }

    @Test
    @DisplayName("응답 body가 없거나 embeddings가 비어 있으면 예외 처리한다")
    void emptyResponseBodyOrEmbeddings() throws Exception {
        try (TestCohereServer server = startServer(200, "", new AtomicReference<>())) {
            CohereEmbeddingClient client = client(server.baseUrl(), "test-api-key", 3);

            assertThatThrownBy(() -> client.embedQuery("query"))
                    .isInstanceOfSatisfying(GeneralException.class, exception ->
                            assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE));
        }
        try (TestCohereServer server = startServer(200, "{\"embeddings\":{\"float\":[]}}", new AtomicReference<>())) {
            CohereEmbeddingClient client = client(server.baseUrl(), "test-api-key", 3);

            assertThatThrownBy(() -> client.embedQuery("query"))
                    .isInstanceOfSatisfying(GeneralException.class, exception ->
                            assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE));
        }
    }

    private CohereEmbeddingClient client(String baseUrl, String apiKey, int dimension) {
        return new CohereEmbeddingClient(
                new CohereProperties(
                        apiKey,
                        baseUrl,
                        new CohereProperties.Embedding(
                                "embed-v4.0",
                                dimension,
                                Duration.ofSeconds(1),
                                Duration.ofSeconds(2)
                        )
                ),
                RestClient.builder()
        );
    }

    private String responseJson(int dimension, int count) throws Exception {
        List<List<Double>> embeddings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<Double> vector = new ArrayList<>();
            for (int j = 0; j < dimension; j++) {
                vector.add((i + 1) * 0.1 + j);
            }
            embeddings.add(vector);
        }
        return objectMapper.writeValueAsString(java.util.Map.of(
                "embeddings",
                java.util.Map.of("float", embeddings)
        ));
    }

    private TestCohereServer startServer(
            int status,
            String responseBody,
            AtomicReference<JsonNode> requestJson
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        server.createContext("/v2/embed", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!requestBody.isBlank()) {
                requestJson.set(objectMapper.readTree(requestBody));
            }
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return new TestCohereServer(server, authorizationHeader);
    }

    private static final class TestCohereServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicReference<String> authorizationHeader;

        private TestCohereServer(HttpServer server, AtomicReference<String> authorizationHeader) {
            this.server = server;
            this.authorizationHeader = authorizationHeader;
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        String authorizationHeader() {
            return authorizationHeader.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
