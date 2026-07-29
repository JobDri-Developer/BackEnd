package com.jobdri.jobdri_api.domain.corpus.service;

import com.jobdri.jobdri_api.global.cohere.CohereEmbeddingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CohereCorpusEmbeddingClient implements CorpusEmbeddingClient {

    private final CohereEmbeddingClient cohereEmbeddingClient;

    @Override
    public List<float[]> embed(List<String> texts, InputType inputType) {
        if (inputType == InputType.SEARCH_QUERY) {
            return List.of(cohereEmbeddingClient.embedQuery(texts == null || texts.isEmpty() ? null : texts.getFirst()));
        }
        return cohereEmbeddingClient.embedDocuments(texts);
    }
}
