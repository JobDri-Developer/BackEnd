package com.jobdri.jobdri_api.domain.masterresume.entity;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationMetricType;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "master_resumes")
public class MasterResume extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(precision = 6, scale = 3)
    private BigDecimal gpa;

    @Column(name = "max_gpa", precision = 6, scale = 3)
    private BigDecimal maxGpa;

    @Column(name = "content_revision", nullable = false)
    private long contentRevision;

    @ElementCollection
    @CollectionTable(name = "master_resume_metrics", joinColumns = @JoinColumn(name = "master_resume_id"))
    @OrderColumn(name = "display_order")
    private List<Metric> metrics = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "master_resume_experiences", joinColumns = @JoinColumn(name = "master_resume_id"))
    @OrderColumn(name = "display_order")
    private List<ExperienceItem> experiences = new ArrayList<>();

    public static MasterResume create(User user) {
        MasterResume resume = new MasterResume();
        resume.user = user;
        return resume;
    }

    public void replace(BigDecimal gpa, BigDecimal maxGpa, List<Metric> metrics, List<ExperienceItem> experiences) {
        this.contentRevision++;
        this.gpa = gpa;
        this.maxGpa = maxGpa;
        this.metrics.clear();
        this.metrics.addAll(metrics);
        this.experiences.clear();
        this.experiences.addAll(experiences);
    }

    @Embeddable
    public record Metric(
            @Enumerated(EnumType.STRING) @Column(name = "metric_type", nullable = false, length = 20)
            JobApplicationMetricType type,
            @Column(name = "metric_name", nullable = false, length = 100) String name,
            @Column(name = "metric_value", nullable = false, length = 200) String value
    ) {}

    @Embeddable
    public record ExperienceItem(
            @Column(name = "experience_name", nullable = false, length = 255) String name,
            @Column(name = "period", length = 100) String period,
            @Column(name = "description", columnDefinition = "TEXT") String description
    ) {}
}
