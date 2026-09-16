package com.jobdri.jobdri_api.domain.jobapplication.dto.request;

import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record JobApplicationCreateRequest(
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
        JobApplicationStage initialStage,

        @Size(max = 100, message = "진행 라벨은 최대 100자까지 입력할 수 있습니다.")
        String currentLabel,
        LocalDateTime currentAt
) {
}
