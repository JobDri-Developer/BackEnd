package com.jobdri.jobdri_api.global.cohere;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.cohere.dto.CohereEmbeddingRequest;
import com.jobdri.jobdri_api.global.cohere.dto.CohereEmbeddingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class CohereEmbeddingClient {
    private static final int MAX_TEXTS_PER_REQUEST = 96;
    private static final String INPUT_TYPE_SEARCH_DOCUMENT = "search_document";
    private static final String INPUT_TYPE_SEARCH_QUERY = "search_query";
    private static final List<String> FLOAT_EMBEDDING_TYPE = List.of("float");

    private final CohereProperties properties;
    private final RestClient restClient;

    public CohereEmbeddingClient(CohereProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(properties))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<float[]> embedDocuments(List<String> texts) {
        return embed(texts, INPUT_TYPE_SEARCH_DOCUMENT);
    }

    public float[] embedQuery(String text) {
        if (!StringUtils.hasText(text)) {
            throw invalidParameter("검색 질의 텍스트는 필수입니다.");
        }
        List<float[]> embeddings = embed(List.of(text), INPUT_TYPE_SEARCH_QUERY);
        if (embeddings.isEmpty()) {
            throw unavailable("Cohere 검색 질의 임베딩 응답이 비어 있습니다.");
        }
        return embeddings.getFirst();
    }

    private List<float[]> embed(List<String> texts, String inputType) {
        validateApiKey();
        List<String> normalizedTexts = validateTexts(texts);
        CohereEmbeddingRequest request = new CohereEmbeddingRequest(
                properties.embedding().model(),
                normalizedTexts,
                inputType,
                FLOAT_EMBEDDING_TYPE,
                properties.embedding().dimension()
        );

        CohereEmbeddingResponse response = callCohere(request);
        return validateResponse(response, normalizedTexts.size());
    }

    private CohereEmbeddingResponse callCohere(CohereEmbeddingRequest request) {
        try {
            return restClient.post()
                    .uri("/v2/embed")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .body(request)
                    .retrieve()
                    .onStatus(
                            status -> status.value() == 429 || status.is5xxServerError(),
                            (ignoredRequest, ignoredResponse) -> {
                                throw unavailable("Cohere Embed API가 일시적으로 응답할 수 없습니다.");
                            }
                    )
                    .onStatus(
                            status -> status.value() == 400
                                    || status.value() == 401
                                    || status.value() == 403
                                    || status.is4xxClientError(),
                            (ignoredRequest, ignoredResponse) -> {
                                throw invalidParameter("Cohere Embed API 요청 또는 설정이 올바르지 않습니다.");
                            }
                    )
                    .body(CohereEmbeddingResponse.class);
        } catch (GeneralException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("Cohere Embed API access failed. reason=resource_access_failure, message={}", e.getMessage());
            throw new GeneralException(
                    GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT,
                    "Cohere Embed API 응답이 지연되었거나 연결할 수 없습니다.",
                    e
            );
        } catch (RestClientException e) {
            log.warn("Cohere Embed API call failed. reason=rest_client_failure, message={}", e.getMessage());
            throw unavailable("Cohere Embed API 호출에 실패했습니다.", e);
        }
    }

    private List<float[]> validateResponse(CohereEmbeddingResponse response, int expectedCount) {
        if (response == null || response.embeddings() == null || response.embeddings().floatValues() == null
                || response.embeddings().floatValues().isEmpty()) {
            throw unavailable("Cohere 임베딩 응답이 비어 있습니다.");
        }

        List<List<Double>> embeddings = response.embeddings().floatValues();
        if (embeddings.size() != expectedCount) {
            throw unavailable("Cohere 임베딩 응답 개수가 요청 개수와 일치하지 않습니다.");
        }

        List<float[]> result = new ArrayList<>();
        for (List<Double> embedding : embeddings) {
            if (embedding == null || embedding.size() != properties.embedding().dimension()) {
                throw unavailable("Cohere 임베딩 차원이 설정값과 일치하지 않습니다.");
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                Double value = embedding.get(i);
                if (value == null) {
                    throw unavailable("Cohere 임베딩 벡터에 비어 있는 값이 포함되어 있습니다.");
                }
                vector[i] = value.floatValue();
            }
            result.add(vector);
        }
        return result;
    }

    private void validateApiKey() {
        if (!properties.hasApiKey()) {
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "Cohere API 키가 설정되지 않았습니다."
            );
        }
    }

    private List<String> validateTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw invalidParameter("임베딩할 텍스트는 1개 이상이어야 합니다.");
        }
        if (texts.size() > MAX_TEXTS_PER_REQUEST) {
            throw invalidParameter("Cohere 임베딩은 한 번에 최대 96개 텍스트만 요청할 수 있습니다.");
        }

        List<String> normalizedTexts = new ArrayList<>();
        for (String text : texts) {
            if (!StringUtils.hasText(text)) {
                throw invalidParameter("임베딩할 텍스트는 비어 있을 수 없습니다.");
            }
            normalizedTexts.add(text.trim());
        }
        return List.copyOf(normalizedTexts);
    }

    private static SimpleClientHttpRequestFactory requestFactory(CohereProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.embedding().connectTimeout());
        requestFactory.setReadTimeout(properties.embedding().readTimeout());
        return requestFactory;
    }

    private GeneralException invalidParameter(String message) {
        return new GeneralException(GeneralErrorCode.INVALID_PARAMETER, message);
    }

    private GeneralException unavailable(String message) {
        return new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, message);
    }

    private GeneralException unavailable(String message, Throwable cause) {
        return new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, message, cause);
    }
}
