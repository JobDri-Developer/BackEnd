package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncStatusResponse;
import com.jobdri.jobdri_api.global.sse.SseSubscriptionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class JobPostingAsyncSseService {

    private static final String EVENT_NAME = "job-posting-status";

    private final SseSubscriptionRegistry sseSubscriptionRegistry;

    public SseEmitter subscribe(JobPostingAsyncStatusResponse initialStatus) {
        return sseSubscriptionRegistry.subscribe(
                channelKey(initialStatus.getTaskId()),
                EVENT_NAME,
                initialStatus,
                isTerminal(initialStatus)
        );
    }

    public void publish(JobPostingAsyncStatusResponse statusResponse) {
        sseSubscriptionRegistry.publish(
                channelKey(statusResponse.getTaskId()),
                EVENT_NAME,
                statusResponse,
                isTerminal(statusResponse)
        );
    }

    private String channelKey(String taskId) {
        return "job-posting:" + taskId;
    }

    private boolean isTerminal(JobPostingAsyncStatusResponse statusResponse) {
        return "SUCCEEDED".equals(statusResponse.getStatus()) || "FAILED".equals(statusResponse.getStatus());
    }
}
