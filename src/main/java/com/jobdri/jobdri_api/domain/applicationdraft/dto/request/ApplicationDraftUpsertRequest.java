package com.jobdri.jobdri_api.domain.applicationdraft.dto.request;

import com.jobdri.jobdri_api.domain.applicationdraft.entity.ApplicationDraftStep;
import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ApplicationDraftUpsertRequest(
        @NotNull(message = "작성 단계는 필수입니다.")
        ApplicationDraftStep step,

        @NotNull(message = "지원 유형은 필수입니다.")
        ApplyType type,

        Long postingId,
        Long middleCategoryId,
        Long smallCategoryId,
        List<Long> selectedQuestionIds
) {
}
