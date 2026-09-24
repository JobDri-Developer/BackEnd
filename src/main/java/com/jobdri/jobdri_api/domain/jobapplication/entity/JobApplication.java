package com.jobdri.jobdri_api.domain.jobapplication.entity;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.CascadeType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "job_applications",
        indexes = {
                @Index(name = "idx_job_applications_user_stage_order", columnList = "user_id,stage,stage_order"),
                @Index(name = "idx_job_applications_user_archived", columnList = "user_id,archived_at")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_applications_user_ingest_key",
                columnNames = {"user_id", "ingest_idempotency_key"}
        )
)
public class JobApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_job_posting_id")
    private JobPosting sourceJobPosting;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mock_apply_id", unique = true)
    private MockApply mockApply;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detail_classification_id")
    private DetailClassification detailClassification;

    @Column(nullable = false, length = 255)
    private String companyName;

    @Column(nullable = false, length = 255)
    private String postingName;

    @Column(nullable = false, length = 255)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CompanySize companySize;

    @Column(columnDefinition = "TEXT")
    private String task;

    @Column(columnDefinition = "TEXT")
    private String requirement;

    @Column(columnDefinition = "TEXT")
    private String preferred;

    @ElementCollection
    @CollectionTable(name = "job_application_required_skills", joinColumns = @JoinColumn(name = "job_application_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "skill_name", nullable = false, length = 50)
    @Builder.Default
    private List<String> requiredSkills = new ArrayList<>();

    private LocalDateTime deadlineAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobApplicationStage stage;

    @Column(name = "stage_order", nullable = false)
    private int stageOrder;

    @Column(length = 100)
    private String currentLabel;

    private LocalDateTime currentAt;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(precision = 6, scale = 3)
    private BigDecimal gpa;

    @Column(name = "max_gpa", precision = 6, scale = 3)
    private BigDecimal maxGpa;

    private LocalDateTime archivedAt;

    @Column(name = "ingest_idempotency_key", length = 100)
    private String ingestIdempotencyKey;

    @OneToMany(mappedBy = "jobApplication", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<JobApplicationChecklistItem> checklistItems = new ArrayList<>();

    @OneToMany(mappedBy = "jobApplication", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<JobApplicationMetric> metrics = new ArrayList<>();

    @OneToMany(mappedBy = "jobApplication", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<JobApplicationEssay> essays = new ArrayList<>();

    public void moveTo(JobApplicationStage stage, int stageOrder) {
        if (stage == null || stageOrder < 0) {
            throw new IllegalArgumentException("단계와 0 이상의 순서가 필요합니다.");
        }
        this.stage = stage;
        this.stageOrder = stageOrder;
    }

    public void archive(LocalDateTime archivedAt) {
        if (archivedAt == null || this.archivedAt != null) {
            throw new IllegalStateException("활성 지원 카드만 보관할 수 있습니다.");
        }
        this.archivedAt = archivedAt;
    }

    public void restore(int stageOrder) {
        if (this.archivedAt == null || stageOrder < 0) {
            throw new IllegalStateException("보관된 지원 카드와 0 이상의 순서가 필요합니다.");
        }
        this.archivedAt = null;
        this.stageOrder = stageOrder;
    }

    public void updateDetails(
            DetailClassification detailClassification,
            String companyName,
            String postingName,
            String jobTitle,
            CompanySize companySize,
            String task,
            String requirement,
            String preferred,
            List<String> requiredSkills,
            LocalDateTime deadlineAt,
            String currentLabel,
            LocalDateTime currentAt,
            String memo,
            BigDecimal gpa,
            BigDecimal maxGpa
    ) {
        this.detailClassification = detailClassification;
        this.companyName = companyName;
        this.postingName = postingName;
        this.jobTitle = jobTitle;
        this.companySize = companySize;
        this.task = task;
        this.requirement = requirement;
        this.preferred = preferred;
        this.requiredSkills.clear();
        this.requiredSkills.addAll(requiredSkills);
        this.deadlineAt = deadlineAt;
        this.currentLabel = currentLabel;
        this.currentAt = currentAt;
        this.memo = memo;
        this.gpa = gpa;
        this.maxGpa = maxGpa;
    }

    public void replaceChecklistItems(List<JobApplicationChecklistItem> items) {
        checklistItems.clear();
        checklistItems.addAll(items);
    }

    public void replaceMetrics(List<JobApplicationMetric> newMetrics) {
        metrics.clear();
        metrics.addAll(newMetrics);
    }

    public void replaceEssays(List<JobApplicationEssay> newEssays) {
        essays.clear();
        essays.addAll(newEssays);
    }

    public static JobApplication create(
            User user,
            JobPosting sourceJobPosting,
            DetailClassification detailClassification,
            String companyName,
            String postingName,
            String jobTitle,
            CompanySize companySize,
            String task,
            String requirement,
            String preferred,
            List<String> requiredSkills,
            LocalDateTime deadlineAt,
            JobApplicationStage stage,
            int stageOrder,
            String currentLabel,
            LocalDateTime currentAt
    ) {
        return JobApplication.builder()
                .user(user)
                .sourceJobPosting(sourceJobPosting)
                .detailClassification(detailClassification)
                .companyName(companyName)
                .postingName(postingName)
                .jobTitle(jobTitle)
                .companySize(companySize)
                .task(task)
                .requirement(requirement)
                .preferred(preferred)
                .requiredSkills(requiredSkills == null ? new ArrayList<>() : new ArrayList<>(requiredSkills))
                .deadlineAt(deadlineAt)
                .stage(stage)
                .stageOrder(stageOrder)
                .currentLabel(currentLabel)
                .currentAt(currentAt)
                .build();
    }

    public static JobApplication createFromIngest(
            User user,
            DetailClassification detailClassification,
            String companyName,
            String postingName,
            String jobTitle,
            String task,
            String requirement,
            String preferred,
            List<String> requiredSkills,
            LocalDateTime deadlineAt,
            int stageOrder,
            String idempotencyKey
    ) {
        JobApplication application = create(
                user, null, detailClassification, companyName, postingName, jobTitle, null,
                task, requirement, preferred, requiredSkills, deadlineAt,
                JobApplicationStage.PLANNED, stageOrder, null, null
        );
        application.ingestIdempotencyKey = idempotencyKey;
        return application;
    }
}
