package com.jobdri.jobdri_api.global.cohere.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CohereEmbeddingResponse(
        Embeddings embeddings
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Embeddings(
            @JsonProperty("float")
            List<List<Double>> floatValues
    ) {
    }
}
