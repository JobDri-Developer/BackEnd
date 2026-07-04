package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestCommand;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingIngestTaskMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobPostingAsyncProcessor {

    private final JobPostingTaskMessagePublisher jobPostingTaskMessagePublisher;

    public void process(String taskId, JobPostingIngestCommand command) {
        jobPostingTaskMessagePublisher.publish(JobPostingIngestTaskMessage.of(taskId, command));
    }
}
