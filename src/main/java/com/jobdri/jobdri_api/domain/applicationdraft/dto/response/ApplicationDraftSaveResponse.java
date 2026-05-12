package com.jobdri.jobdri_api.domain.applicationdraft.dto.response;

import com.jobdri.jobdri_api.domain.applicationdraft.entity.ApplicationDraftStep;

import java.time.LocalDateTime;

public record ApplicationDraftSaveResponse(
        Long draftId,
        ApplicationDraftStep step,
        LocalDateTime savedAt
) {
}
