package com.jobdri.jobdri_api.domain.mockapply.dto.response;

import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;

import java.time.LocalDateTime;

public record MockApplyUpdateNameResponse(
        Long mockApplyId,
        String name,
        LocalDateTime updatedAt
) {
    public static MockApplyUpdateNameResponse from(MockApply mockApply) {
        return new MockApplyUpdateNameResponse(
                mockApply.getId(),
                mockApply.getDisplayName(),
                mockApply.getUpdatedAt()
        );
    }
}
