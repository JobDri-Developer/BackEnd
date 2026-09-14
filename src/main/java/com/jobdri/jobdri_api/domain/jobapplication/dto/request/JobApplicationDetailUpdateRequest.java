package com.jobdri.jobdri_api.domain.jobapplication.dto.request;

import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record JobApplicationDetailUpdateRequest(
        @NotNull(message = "마지막 조회 수정 시각은 필수입니다.")
        LocalDateTime lastKnownUpdatedAt,

        @NotBlank(message = "회사명은 필수입니다.")
        @Size(max = 255, message = "회사명은 최대 255자까지 입력할 수 있습니다.")
        String companyName,

        @NotBlank(message = "공고명은 필수입니다.")
        @Size(max = 255, message = "공고명은 최대 255자까지 입력할 수 있습니다.")
        String postingName,

        @NotBlank(message = "직무명은 필수입니다.")
        @Size(max = 255, message = "직무명은 최대 255자까지 입력할 수 있습니다.")
        String jobTitle,

        CompanySize companySize,
        Long detailClassificationId,

        @Size(max = 4000, message = "주요 업무는 최대 4000자까지 입력할 수 있습니다.")
        String task,

        @Size(max = 4000, message = "자격 요건은 최대 4000자까지 입력할 수 있습니다.")
        String requirement,

        @Size(max = 4000, message = "우대 사항은 최대 4000자까지 입력할 수 있습니다.")
        String preferred,

        @Size(max = 20, message = "기술 태그는 최대 20개까지 입력할 수 있습니다.")
        List<@NotBlank(message = "기술 태그는 비어 있을 수 없습니다.") @Size(max = 50, message = "기술 태그는 각각 최대 50자까지 입력할 수 있습니다.") String> requiredSkills,

        LocalDateTime deadlineAt,

        @Size(max = 100, message = "진행 라벨은 최대 100자까지 입력할 수 있습니다.")
        String currentLabel,
        LocalDateTime currentAt,

        @Size(max = 10000, message = "메모는 최대 10000자까지 입력할 수 있습니다.")
        String memo,

        @DecimalMin(value = "0", message = "학점은 0 이상이어야 합니다.")
        @Digits(integer = 3, fraction = 3, message = "학점은 정수 3자리, 소수 3자리까지 입력할 수 있습니다.")
        BigDecimal gpa,

        @DecimalMin(value = "0", message = "학점 만점은 0 이상이어야 합니다.")
        @Digits(integer = 3, fraction = 3, message = "학점 만점은 정수 3자리, 소수 3자리까지 입력할 수 있습니다.")
        BigDecimal maxGpa,

        @Size(max = 100, message = "체크리스트는 최대 100개까지 입력할 수 있습니다.")
        List<@Valid JobApplicationChecklistItemRequest> checklistItems,

        List<@Valid JobApplicationMetricRequest> metrics,

        @Size(max = 20, message = "자기소개서는 최대 20개까지 입력할 수 있습니다.")
        List<@Valid JobApplicationEssayRequest> essays
) {
    @AssertTrue(message = "학점과 만점은 모두 입력하거나 모두 비워야 하며, 학점은 만점 이하여야 합니다.")
    public boolean isGpaRangeValid() {
        if (gpa == null || maxGpa == null) {
            return gpa == null && maxGpa == null;
        }
        return gpa.compareTo(maxGpa) <= 0;
    }
}
