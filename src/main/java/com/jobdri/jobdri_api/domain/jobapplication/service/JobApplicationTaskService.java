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

    public List<JobApplicationTaskResponse> list(User user, Long applicationId) {
        requireOwnedApplication(user, applicationId, false);
        return tasks.findAllByJobApplicationIdOrderByIdAsc(applicationId).stream()
                .map(JobApplicationTaskResponse::from)
                .toList();
    }

    public JobApplicationTaskResponse get(User user, Long applicationId, Long taskId) {
        requireOwnedApplication(user, applicationId, false);
        return JobApplicationTaskResponse.from(findTask(applicationId, taskId));
    }

    @Transactional
    public JobApplicationTaskResponse create(User user, Long applicationId, JobApplicationTaskRequest request) {
        JobApplication application = requireOwnedApplication(user, applicationId, true);
        JobApplicationTask task = JobApplicationTask.create(application, request.title().trim(), request.dueDate(),
                request.memo(), checklistItems(request));
        return JobApplicationTaskResponse.from(tasks.save(task));
    }

    @Transactional
    public JobApplicationTaskResponse update(User user, Long applicationId, Long taskId,
                                             JobApplicationTaskRequest request) {
        requireOwnedApplication(user, applicationId, true);
        JobApplicationTask task = findTask(applicationId, taskId);
        task.update(request.title().trim(), request.dueDate(), request.memo(), checklistItems(request));
        return JobApplicationTaskResponse.from(task);
    }

    @Transactional
    public JobApplicationTaskResponse setCompleted(User user, Long applicationId, Long taskId, boolean completed) {
        requireOwnedApplication(user, applicationId, true);
        JobApplicationTask task = findTask(applicationId, taskId);
        task.setCompleted(completed);
        return JobApplicationTaskResponse.from(task);
    }

    @Transactional
    public void delete(User user, Long applicationId, Long taskId) {
        requireOwnedApplication(user, applicationId, true);
        tasks.delete(findTask(applicationId, taskId));
    }

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

    private JobApplicationTask findTask(Long applicationId, Long taskId) {
        return tasks.findByIdAndJobApplicationId(taskId, applicationId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.JOB_APPLICATION_TASK_NOT_FOUND));
    }

    private List<JobApplicationTask.ChecklistItem> checklistItems(JobApplicationTaskRequest request) {
        if (request.checklistItems() == null) {
            return List.of();
        }
        return request.checklistItems().stream()
                .map(item -> new JobApplicationTask.ChecklistItem(item.content().trim(), item.completed()))
                .toList();
    }
}
