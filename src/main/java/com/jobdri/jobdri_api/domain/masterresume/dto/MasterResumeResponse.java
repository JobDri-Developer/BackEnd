package com.jobdri.jobdri_api.domain.masterresume.dto;

import com.jobdri.jobdri_api.domain.masterresume.entity.MasterResume;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record MasterResumeResponse(
        BigDecimal gpa, BigDecimal maxGpa,
        List<MasterResume.Metric> metrics,
        List<MasterResume.ExperienceItem> experiences,
        LocalDateTime updatedAt,
        long contentRevision
) {
    public static MasterResumeResponse empty() {
        return new MasterResumeResponse(null, null, List.of(), List.of(), null, 0);
    }

    public static MasterResumeResponse from(MasterResume resume) {
        return new MasterResumeResponse(resume.getGpa(), resume.getMaxGpa(),
                List.copyOf(resume.getMetrics()), List.copyOf(resume.getExperiences()),
                resume.getUpdatedAt(), resume.getContentRevision());
    }
}
