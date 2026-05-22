package com.jobdri.jobdri_api.domain.mockapply.dto.response;

public record MockApplySequenceResponse(
        Long jobPostingId,
        Long mockApplyId,
        int totalCount,
        int sequence
) {
}
