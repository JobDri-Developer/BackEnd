package com.jobdri.jobdri_api.domain.analysis.dto.response;

public record MissingKeywordResponse(
        String keyword,
        MissingKeywordSource source
) {
}
