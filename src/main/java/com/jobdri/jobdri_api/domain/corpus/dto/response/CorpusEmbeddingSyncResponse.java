package com.jobdri.jobdri_api.domain.corpus.dto.response;

public record CorpusEmbeddingSyncResponse(
        int jobPostingEmbeddingsUpserted,
        int questionEmbeddingsUpserted,
        String embeddingModel
) {
}
