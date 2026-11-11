package com.jobdri.jobdri_api.domain.jobapplication.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record JobApplicationTaskRequest(
        @NotBlank @Size(max = 200) String title,
        LocalDate dueDate,
        @Size(max = 2000) String memo,
        @Size(max = 100, message = "하위 체크리스트는 최대 100개까지 입력할 수 있습니다.")
        List<@NotNull @Valid JobApplicationChecklistItemRequest> checklistItems
) {
    public record Completion(@NotNull Boolean completed) {
    }
}
