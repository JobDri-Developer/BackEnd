package com.jobdri.jobdri_api.domain.jobapplication.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "job_application_checklist_items", indexes =
        @Index(name = "idx_job_application_checklist_application", columnList = "job_application_id,display_order"))
public class JobApplicationChecklistItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @Column(nullable = false, length = 200)
    private String content;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public static JobApplicationChecklistItem create(
            JobApplication application,
            String content,
            boolean completed,
            int displayOrder
    ) {
        JobApplicationChecklistItem item = new JobApplicationChecklistItem();
        item.jobApplication = application;
        item.content = content;
        item.completed = completed;
        item.displayOrder = displayOrder;
        return item;
    }
}
