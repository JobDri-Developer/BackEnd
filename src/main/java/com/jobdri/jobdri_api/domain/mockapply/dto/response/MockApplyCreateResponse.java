package com.jobdri.jobdri_api.domain.mockapply.dto.response;

import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;

public record MockApplyCreateResponse(
        Long jobPostingId,
        Long mockApplyId,
        ApplyType applyType
) {
    public static MockApplyCreateResponse from(MockApply mockApply) {
        return new MockApplyCreateResponse(
                mockApply.getJobPosting().getId(),
                mockApply.getId(),
                mockApply.getApplyType()
        );
    }
}
