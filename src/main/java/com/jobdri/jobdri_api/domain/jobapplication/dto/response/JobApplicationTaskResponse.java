package com.jobdri.jobdri_api.domain.jobapplication.dto.response;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationTask;

import java.time.LocalDate;
import java.util.List;

public record JobApplicationTaskResponse(
        Long taskId,
        String title,
        LocalDate dueDate,
        String memo,
        boolean completed,
        List<ChecklistItem> checklistItems,
        int completedChecklistCount,
        int totalChecklistCount
) {
    public static JobApplicationTaskResponse from(JobApplicationTask task) {
        List<ChecklistItem> items = task.getChecklistItems().stream()
                .map(item -> new ChecklistItem(item.content(), item.completed()))
                .toList();
        int completedCount = (int) items.stream().filter(ChecklistItem::completed).count();
        return new JobApplicationTaskResponse(task.getId(), task.getTitle(), task.getDueDate(), task.getMemo(),
                task.isCompleted(), items, completedCount, items.size());
    }

    public record ChecklistItem(String content, boolean completed) {
    }
}
