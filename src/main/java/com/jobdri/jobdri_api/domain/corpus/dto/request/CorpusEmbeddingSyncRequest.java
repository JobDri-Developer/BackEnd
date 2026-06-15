package com.jobdri.jobdri_api.domain.corpus.dto.request;

import jakarta.validation.constraints.Positive;

public record CorpusEmbeddingSyncRequest(
        @Positive(message = "limit는 1 이상이어야 합니다.")
        Integer limit
) {
}
