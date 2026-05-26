package com.jobdri.jobdri_api.domain.mockapply.dto.response;

import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;

public record MockApplyRetryResponse(
        Long sourceMockApplyId,
        Long jobPostingId,
        Long mockApplyId,
        ApplyType applyType,
        MockApplyStatus status,
        int sequence
) {
    public static MockApplyRetryResponse of(Long sourceMockApplyId, MockApply mockApply) {
        return new MockApplyRetryResponse(
                sourceMockApplyId,
                mockApply.getJobPosting().getId(),
                mockApply.getId(),
                mockApply.getApplyType(),
                mockApply.getStatus(),
                mockApply.getSequence()
        );
    }
}
