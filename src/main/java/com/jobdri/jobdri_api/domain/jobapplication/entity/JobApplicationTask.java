package com.jobdri.jobdri_api.domain.jobapplication.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "job_application_tasks", indexes =
        @Index(name = "idx_job_application_tasks_application", columnList = "job_application_id,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobApplicationTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_application_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private JobApplication jobApplication;

    @Column(nullable = false, length = 200)
    private String title;

    private LocalDate dueDate;

    @Column(length = 2000)
    private String memo;

    @Column(nullable = false)
    private boolean completed;

    @ElementCollection
    @CollectionTable(name = "job_application_task_checklist_items",
            joinColumns = @JoinColumn(name = "task_id", nullable = false))
    @OrderColumn(name = "display_order", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<ChecklistItem> checklistItems = new ArrayList<>();

    public static JobApplicationTask create(JobApplication application, String title, LocalDate dueDate,
                                            String memo, List<ChecklistItem> checklistItems) {
        JobApplicationTask task = new JobApplicationTask();
        task.jobApplication = application;
        task.update(title, dueDate, memo, checklistItems);
        return task;
    }

    public void update(String title, LocalDate dueDate, String memo, List<ChecklistItem> checklistItems) {
        this.title = title;
        this.dueDate = dueDate;
        this.memo = memo;
        this.checklistItems.clear();
        this.checklistItems.addAll(checklistItems);
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    @Embeddable
    public record ChecklistItem(@Column(nullable = false, length = 200) String content,
                                @Column(nullable = false) boolean completed) {
    }
}
