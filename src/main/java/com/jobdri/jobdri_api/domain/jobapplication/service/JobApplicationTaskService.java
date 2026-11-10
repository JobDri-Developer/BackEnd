package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationTaskRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationTaskResponse;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationTask;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationTaskRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobApplicationTaskService {
    private final JobApplicationRepository applications;
    private final JobApplicationTaskRepository tasks;
    private final UserService userService;

    /** Lists an owned card's tasks in registration order. */
    public List<JobApplicationTaskResponse> list(User user, Long applicationId) {
        requireOwnedApplication(user, applicationId, false);
        return tasks.findAllByJobApplicationIdOrderByIdAsc(applicationId).stream()
                .map(JobApplicationTaskResponse::from)
                .toList();
    }

    /** Reads a task belonging to the specified owned card. */
    public JobApplicationTaskResponse get(User user, Long applicationId, Long taskId) {
        requireOwnedApplication(user, applicationId, false);
        return JobApplicationTaskResponse.from(findTask(applicationId, taskId));
    }

    /** Creates a task under the card lock. */
    @Transactional
    public JobApplicationTaskResponse create(User user, Long applicationId, JobApplicationTaskRequest request) {
        JobApplication application = requireOwnedApplication(user, applicationId, true);
        JobApplicationTask task = JobApplicationTask.create(application, request.title().trim(), request.dueDate(),
                request.memo(), checklistItems(request));
        return JobApplicationTaskResponse.from(tasks.save(task));
    }

    /** Replaces editable fields after acquiring the card lock. */
    @Transactional
    public JobApplicationTaskResponse update(User user, Long applicationId, Long taskId,
                                             JobApplicationTaskRequest request) {
        requireOwnedApplication(user, applicationId, true);
        JobApplicationTask task = findTask(applicationId, taskId);
        task.update(request.title().trim(), request.dueDate(), request.memo(), checklistItems(request));
        return JobApplicationTaskResponse.from(task);
    }

    /** Sets completion under the card lock without changing checklist items. */
    @Transactional
    public JobApplicationTaskResponse setCompleted(User user, Long applicationId, Long taskId, boolean completed) {
        requireOwnedApplication(user, applicationId, true);
        JobApplicationTask task = findTask(applicationId, taskId);
        task.setCompleted(completed);
        return JobApplicationTaskResponse.from(task);
    }

    /** Removes the task under the card lock. */
    @Transactional
    public void delete(User user, Long applicationId, Long taskId) {
        requireOwnedApplication(user, applicationId, true);
        tasks.delete(findTask(applicationId, taskId));
    }

    /** Checks card ownership and acquires the card write lock for mutations. */
    private JobApplication requireOwnedApplication(User user, Long applicationId, boolean forUpdate) {
        Long userId = userService.validateUser(user).getId();
        // Lock the same card row as permanent deletion before mutating any child task.
        JobApplication application = (forUpdate ? applications.findByIdForUpdate(applicationId)
                : applications.findById(applicationId))
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.JOB_APPLICATION_NOT_FOUND));
        if (!application.getUser().getId().equals(userId)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 지원 카드에 접근할 수 없습니다.");
        }
        return application;
    }

    /** Resolves a task by both its identifier and its parent card. */
    private JobApplicationTask findTask(Long applicationId, Long taskId) {
        return tasks.findByIdAndJobApplicationId(taskId, applicationId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.JOB_APPLICATION_TASK_NOT_FOUND));
    }

    /** Normalizes checklist content while retaining request order. */
    private List<JobApplicationTask.ChecklistItem> checklistItems(JobApplicationTaskRequest request) {
        if (request.checklistItems() == null) {
            return List.of();
        }
        return request.checklistItems().stream()
                .map(item -> new JobApplicationTask.ChecklistItem(item.content().trim(), item.completed()))
                .toList();
    }
}
