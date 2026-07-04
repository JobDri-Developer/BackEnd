package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask.FailureReason;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask.TaskStatus;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobPostingWorkerBridgeService {

    private final JobPostingAsyncTaskService jobPostingAsyncTaskService;
    private final JobPostingAsyncTaskRepository jobPostingAsyncTaskRepository;
    private final JobPostingImageStorageService jobPostingImageStorageService;
    private final JobPostingClassificationService jobPostingClassificationService;
    private final JobPostingService jobPostingService;
    private final UserService userService;

    public void markRunning(String taskId, String workerId, int retryCount, Instant submittedAt) {
        jobPostingAsyncTaskService.markRunning(taskId, workerId, retryCount, submittedAt);
    }

    @Transactional
    public JobPostingIngestResponse completeTask(String taskId, JobPostingIngestResponse result) {
        return jobPostingAsyncTaskService.markSuccess(taskId, result);
    }

    public void markRetry(String taskId, FailureReason failureReason, String errorMessage, int retryCount) {
        jobPostingAsyncTaskService.markRetryScheduled(taskId, failureReason, errorMessage, retryCount);
    }

    public void failTask(String taskId, FailureReason failureReason, String errorMessage, int retryCount) {
        jobPostingAsyncTaskService.markFailed(taskId, failureReason, errorMessage, retryCount);
    }

    public String createReadableImageUrl(Long userId, String imageObjectKey) {
        return jobPostingImageStorageService.createReadableImageUrl(userId, imageObjectKey);
    }

    public List<JobPostingClassificationCandidateResponse> findCandidates(JobPostingExtractResponse extracted) {
        return jobPostingClassificationService.findCandidates(extracted, 5);
    }

    @Transactional
    public JobPostingIngestResponse finalizeAndComplete(
            String taskId,
            Long userId,
            JobPostingExtractResponse extracted,
            List<JobPostingClassificationCandidateResponse> candidates,
            com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationResultResponse classification,
            JobPostingGenerateResponse generated
    ) {
        JobPostingAsyncTask task = jobPostingAsyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_POSTING_ASYNC_TASK_NOT_FOUND,
                        "해당 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));

        if (task.getStatus() == TaskStatus.SUCCEEDED) {
            return jobPostingAsyncTaskService.getTask(taskId).getResult();
        }
        if (task.getStatus() == TaskStatus.FAILED) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이미 실패 처리된 채용 공고 비동기 작업입니다. taskId=" + taskId
            );
        }

        var saved = jobPostingService.createJobPosting(
                userService.getUser(userId),
                new JobPostingCreateRequest(
                        fallbackCompanyName(extracted.companyName()),
                        null,
                        classification.detailClassificationId(),
                        generated.task(),
                        generated.requirements(),
                        generated.preferredQualifications()
                )
        );

        JobPostingIngestResponse result = new JobPostingIngestResponse(
                true,
                "채용 공고 추출 및 저장에 성공했습니다.",
                extracted,
                candidates,
                classification,
                generated,
                saved
        );
        return jobPostingAsyncTaskService.markSuccess(taskId, result);
    }

    private String fallbackCompanyName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return "미분류 회사";
        }
        return companyName;
    }
}
