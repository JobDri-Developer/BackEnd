package com.jobdri.jobdri_api.domain.corpus.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CohereCorpusEmbeddingClient implements CorpusEmbeddingClient {

    private final RestClient.Builder restClientBuilder;

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

        RestClient client = restClientBuilder
                .baseUrl("https://api.cohere.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cohereApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        EmbedResponse response = client.post()
                .uri("/v2/embed")
                .body(new EmbedRequest(
                        texts,
                        embeddingModel,
                        inputType.value(),
                        outputDimension,
                        List.of("float")
                ))
                .retrieve()
                .body(EmbedResponse.class);

        if (response == null || response.embeddings() == null || response.embeddings().floatEmbeddings() == null) {
            throw new IllegalStateException("Cohere 임베딩 응답이 비어 있습니다.");
        }

        return response.embeddings().floatEmbeddings().stream()
                .map(this::toFloatArray)
                .toList();
    }

    private float[] toFloatArray(List<Double> values) {
        float[] array = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i).floatValue();
        }
        return array;
    }

    private record EmbedRequest(
            List<String> texts,
            String model,
            String input_type,
            Integer output_dimension,
            List<String> embedding_types
    ) {
    }

    private record EmbedResponse(Embeddings embeddings) {
    }

    private record Embeddings(
            @com.fasterxml.jackson.annotation.JsonProperty("float")
            List<List<Double>> floatEmbeddings
    ) {
    }
}
