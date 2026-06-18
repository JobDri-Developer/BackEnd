package com.jobdri.jobdri_api.domain.corpus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CohereCorpusEmbeddingClient implements CorpusEmbeddingClient {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${cohere.api.key:}")
    private String cohereApiKey;

    @Value("${app.corpus.embedding.model:embed-v4.0}")
    private String embeddingModel;

    @Value("${app.corpus.embedding.output-dimension:1024}")
    private int outputDimension;

    @Override
    public List<float[]> embed(List<String> texts, InputType inputType) {
        if (!StringUtils.hasText(cohereApiKey)) {
            throw new IllegalStateException("Cohere API 키가 설정되지 않았습니다.");
        }
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        RestClient client = restClientBuilder
                .baseUrl("https://api.cohere.com")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cohereApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        String responseBody = client.post()
                .uri("/v2/embed")
                .body(new EmbedRequest(
                        texts,
                        embeddingModel,
                        inputType.value(),
                        outputDimension,
                        List.of("float")
                ))
                .retrieve()
                .body(String.class);

        return parseEmbeddings(responseBody);
    }

    private float[] toFloatArray(List<Double> values) {
        float[] array = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i).floatValue();
        }
        return array;
    }

    private List<float[]> parseEmbeddings(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("Cohere 임베딩 응답이 비어 있습니다.");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode floatEmbeddings = root.path("embeddings").path("float");
            if (!floatEmbeddings.isArray()) {
                throw new IllegalStateException("Cohere 임베딩 응답 형식이 예상과 다릅니다.");
            }

            List<float[]> result = new java.util.ArrayList<>();
            for (JsonNode embeddingNode : floatEmbeddings) {
                if (!embeddingNode.isArray()) {
                    throw new IllegalStateException("Cohere 임베딩 벡터 형식이 예상과 다릅니다.");
                }

                float[] vector = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = embeddingNode.get(i).floatValue();
                }
                result.add(vector);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Cohere 임베딩 응답 파싱에 실패했습니다.", e);
        }
    }

    private record EmbedRequest(
            List<String> texts,
            String model,
            String input_type,
            Integer output_dimension,
            List<String> embedding_types
    ) {
    }

}
