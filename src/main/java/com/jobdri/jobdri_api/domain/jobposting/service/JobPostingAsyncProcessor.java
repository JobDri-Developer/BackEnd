package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestCommand;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobPostingAsyncProcessor {

    private final JobPostingAsyncTaskService jobPostingAsyncTaskService;
    private final JobPostingIngestService jobPostingIngestService;

    @Async("jobPostingAsyncExecutor")
    public void process(String taskId, JobPostingIngestCommand command) {
        jobPostingAsyncTaskService.markRunning(taskId);

        try {
            JobPostingIngestResponse result = jobPostingIngestService.ingestAndCreate(command);
            jobPostingAsyncTaskService.markSuccess(taskId, result);
        } catch (Exception e) {
            log.error("채용 공고 비동기 처리 실패: taskId={}", taskId, e);
            jobPostingAsyncTaskService.markFailed(taskId, e.getMessage());
        }
    }
}
