package com.jobdri.jobdri_api.global.cohere.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CohereEmbeddingResponse(
        Embeddings embeddings
) {
    public record Embeddings(
            @JsonProperty("float")
            List<List<Double>> floatValues
    ) {
    }
}
