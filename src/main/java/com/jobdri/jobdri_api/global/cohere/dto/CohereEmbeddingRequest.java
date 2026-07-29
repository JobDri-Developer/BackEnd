package com.jobdri.jobdri_api.global.cohere.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CohereEmbeddingRequest(
        String model,
        List<String> texts,
        @JsonProperty("input_type")
        String inputType,
        @JsonProperty("embedding_types")
        List<String> embeddingTypes,
        @JsonProperty("output_dimension")
        Integer outputDimension
) {
}
