package com.jobdri.jobdri_api.domain.applicationdraft.dto.response;

import com.jobdri.jobdri_api.domain.applicationdraft.entity.ApplicationDraftStep;
import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicationDraftResponse(
        Long draftId,
        ApplicationDraftStep step,
        ApplyType type,
        Long postingId,
        Long middleCategoryId,
        Long smallCategoryId,
        List<Long> selectedQuestionIds,
        LocalDateTime savedAt
) {
}
