package com.jobdri.jobdri_api.domain.jobapplication.entity;

import jakarta.persistence.Column;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "job_application_metrics", indexes =
        @Index(name = "idx_job_application_metrics_application", columnList = "job_application_id,display_order"))
public class JobApplicationMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobApplicationMetricType type;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "metric_value", nullable = false, length = 200)
    private String value;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public static JobApplicationMetric create(
            JobApplication application,
            JobApplicationMetricType type,
            String name,
            String value,
            int displayOrder
    ) {
        JobApplicationMetric metric = new JobApplicationMetric();
        metric.jobApplication = application;
        metric.type = type;
        metric.name = name;
        metric.value = value;
        metric.displayOrder = displayOrder;
        return metric;
    }
}
