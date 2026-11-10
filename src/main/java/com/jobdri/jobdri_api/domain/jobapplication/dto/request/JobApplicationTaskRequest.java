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
        List<@NotNull @Valid JobApplicationChecklistItemRequest> checklistItems
) {
    public record Completion(@NotNull Boolean completed) {
    }
}
