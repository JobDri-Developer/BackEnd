package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestCommand;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncCancelResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncSubmitResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobPostingAsyncFacadeService {

    private final JobPostingAsyncTaskService jobPostingAsyncTaskService;
    private final JobPostingAsyncProcessor jobPostingAsyncProcessor;
    private final UserService userService;
    private final JobPostingImageStorageService jobPostingImageStorageService;

    public JobPostingAsyncSubmitResponse submit(User user, JobPostingIngestRequest request) {
        User validatedUser = userService.validateUser(user);
        JobPostingIngestCommand command = snapshot(validatedUser, request);
        JobPostingAsyncTask task = jobPostingAsyncTaskService.createPendingTask(validatedUser.getId());
        String taskId = task.getTaskId();

        try {
            jobPostingAsyncProcessor.process(taskId, command, task.getMaxRetryCount());
            return new JobPostingAsyncSubmitResponse(
                    taskId,
                    "PENDING",
                    "채용 공고 비동기 작업이 접수되었습니다."
            );
        } catch (RuntimeException e) {
            jobPostingAsyncTaskService.deleteTask(taskId);
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "현재 비동기 작업을 접수할 수 없습니다. 잠시 후 다시 시도해주세요."
            );
        }
    }

    public JobPostingAsyncStatusResponse getTask(User user, String taskId) {
        User validatedUser = userService.validateUser(user);
        return jobPostingAsyncTaskService.getTask(validatedUser, taskId);
    }

    public JobPostingAsyncStatusResponse getTaskInternal(String taskId) {
        return jobPostingAsyncTaskService.getTask(taskId);
    }

    public JobPostingAsyncCancelResponse cancel(User user, String taskId) {
        User validatedUser = userService.validateUser(user);
        return jobPostingAsyncTaskService.cancelTask(validatedUser, taskId);
    }

    private JobPostingIngestCommand snapshot(User user, JobPostingIngestRequest request) {
        var imageObjectKeys = jobPostingImageStorageService.normalizeImageObjectKeys(
                request.imageObjectKey(),
                request.imageObjectKeys()
        );
        JobPostingIngestInputValidator.validate(request.rawText(), imageObjectKeys);
        return JobPostingIngestCommand.builder()
                .userId(user.getId())
                .rawText(request.rawText())
                .imageObjectKey(imageObjectKeys.isEmpty() ? null : imageObjectKeys.get(0))
                .imageObjectKeys(imageObjectKeys)
                .build();
    }
}
