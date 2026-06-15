package com.jobdri.jobdri_api.domain.corpus.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OpenAiCorpusEmbeddingClient implements CorpusEmbeddingClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    @Value("${app.corpus.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    @Override
    public List<float[]> embed(List<String> texts) {
        if (!StringUtils.hasText(openAiApiKey)) {
            throw new IllegalStateException("OpenAI API 키가 설정되지 않았습니다.");
        }
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        RestClient client = restClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        EmbeddingResponse response = client.post()
                .uri("/embeddings")
                .body(new EmbeddingRequest(embeddingModel, texts))
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data() == null) {
            throw new IllegalStateException("임베딩 응답이 비어 있습니다.");
        }

        return response.data().stream()
                .sorted(Comparator.comparingInt(EmbeddingItem::index))
                .map(EmbeddingItem::embedding)
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

    private record EmbeddingRequest(String model, List<String> input) {
    }

    private record EmbeddingResponse(List<EmbeddingItem> data) {
    }

    private record EmbeddingItem(int index, List<Double> embedding) {
    }
}
