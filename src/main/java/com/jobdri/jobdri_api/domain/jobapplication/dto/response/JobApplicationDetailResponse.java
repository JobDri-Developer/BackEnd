package com.jobdri.jobdri_api.domain.jobapplication.dto.response;

import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationMetricType;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobApplicationDetailResponse {
    private Long jobApplicationId;
    private Long sourceJobPostingId;
    private Long mockApplyId;
    private String companyName;
    private String postingName;
    private String jobTitle;
    private CompanySize companySize;
    private Long detailClassificationId;
    private String detailClassificationName;
    private String task;
    private String requirement;
    private String preferred;
    private List<String> requiredSkills;
    private LocalDateTime deadlineAt;
    private JobApplicationStage stage;
    private int stageOrder;
    private String currentLabel;
    private LocalDateTime currentAt;
    private String memo;
    private BigDecimal gpa;
    private BigDecimal maxGpa;
    private List<ChecklistItem> checklistItems;
    private List<Metric> metrics;
    private List<Essay> essays;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static JobApplicationDetailResponse from(JobApplication application) {
        return JobApplicationDetailResponse.builder()
                .jobApplicationId(application.getId())
                .sourceJobPostingId(application.getSourceJobPosting() == null ? null : application.getSourceJobPosting().getId())
                .mockApplyId(application.getMockApply() == null ? null : application.getMockApply().getId())
                .companyName(application.getCompanyName())
                .postingName(application.getPostingName())
                .jobTitle(application.getJobTitle())
                .companySize(application.getCompanySize())
                .detailClassificationId(application.getDetailClassification() == null ? null : application.getDetailClassification().getId())
                .detailClassificationName(application.getDetailClassification() == null ? null : application.getDetailClassification().getDetailName())
                .task(application.getTask())
                .requirement(application.getRequirement())
                .preferred(application.getPreferred())
                .requiredSkills(List.copyOf(application.getRequiredSkills()))
                .deadlineAt(application.getDeadlineAt())
                .stage(application.getStage())
                .stageOrder(application.getStageOrder())
                .currentLabel(application.getCurrentLabel())
                .currentAt(application.getCurrentAt())
                .memo(application.getMemo())
                .gpa(application.getGpa())
                .maxGpa(application.getMaxGpa())
                .checklistItems(application.getChecklistItems().stream().map(ChecklistItem::from).toList())
                .metrics(application.getMetrics().stream().map(Metric::from).toList())
                .essays(application.getEssays().stream().map(Essay::from).toList())
                .archivedAt(application.getArchivedAt())
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }

    public record ChecklistItem(Long checklistItemId, String content, boolean completed, int displayOrder) {
        private static ChecklistItem from(com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationChecklistItem item) {
            return new ChecklistItem(item.getId(), item.getContent(), item.isCompleted(), item.getDisplayOrder());
        }
    }

    public record Metric(
            Long metricId,
            JobApplicationMetricType type,
            String name,
            String value,
            int displayOrder
    ) {
        private static Metric from(com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationMetric metric) {
            return new Metric(metric.getId(), metric.getType(), metric.getName(), metric.getValue(), metric.getDisplayOrder());
        }
    }

    public record Essay(Long essayId, String question, String answer, int displayOrder) {
        private static Essay from(com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationEssay essay) {
            return new Essay(essay.getId(), essay.getQuestion(), essay.getAnswer(), essay.getDisplayOrder());
        }
    }
}
