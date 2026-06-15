package com.jobdri.jobdri_api.domain.corpus.service;

import java.util.List;

public interface CorpusEmbeddingClient {
    List<float[]> embed(List<String> texts, InputType inputType);

    default List<float[]> embedDocuments(List<String> texts) {
        return embed(texts, InputType.SEARCH_DOCUMENT);
    }

    default float[] embedQuery(String text) {
        List<float[]> embeddings = embed(List.of(text), InputType.SEARCH_QUERY);
        if (embeddings.isEmpty()) {
            throw new IllegalStateException("쿼리 임베딩 결과가 비어 있습니다.");
        }
        return embeddings.getFirst();
    }

    enum InputType {
        SEARCH_DOCUMENT("search_document"),
        SEARCH_QUERY("search_query");

        private final String value;

        InputType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
