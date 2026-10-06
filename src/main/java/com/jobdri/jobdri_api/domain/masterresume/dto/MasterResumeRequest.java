package com.jobdri.jobdri_api.domain.masterresume.dto;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationMetricType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record MasterResumeRequest(
        @DecimalMin("0") @Digits(integer = 3, fraction = 3) BigDecimal gpa,
        @DecimalMin("0") @Digits(integer = 3, fraction = 3) BigDecimal maxGpa,
        List<@Valid Metric> metrics,
        List<@Valid Experience> experiences,
        LocalDateTime lastKnownUpdatedAt
) {
    public MasterResumeRequest(BigDecimal gpa, BigDecimal maxGpa, List<Metric> metrics,
                               List<Experience> experiences) {
        this(gpa, maxGpa, metrics, experiences, null);
    }
    public record Metric(
            JobApplicationMetricType type,
            @Size(max = 100) String name,
            @Size(max = 200) String value
    ) {}
    public record Experience(
            @Size(max = 255) String name,
            @Size(max = 100) String period,
            @Size(max = 10000) String description
    ) {}
}
