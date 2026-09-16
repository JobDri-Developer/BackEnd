package com.jobdri.jobdri_api.domain.jobapplication.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "job_application_essays", indexes =
        @Index(name = "idx_job_application_essays_application", columnList = "job_application_id,display_order"))
public class JobApplicationEssay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @Column(nullable = false, length = 1000)
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public static JobApplicationEssay create(JobApplication application, String question, String answer, int displayOrder) {
        JobApplicationEssay essay = new JobApplicationEssay();
        essay.jobApplication = application;
        essay.question = question;
        essay.answer = answer;
        essay.displayOrder = displayOrder;
        return essay;
    }
}
