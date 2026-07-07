package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncStatusResponse;
import com.jobdri.jobdri_api.global.sse.SseSubscriptionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class AnalysisAsyncSseService {

    private static final String EVENT_NAME = "analysis-status";

    private final SseSubscriptionRegistry sseSubscriptionRegistry;

    public SseEmitter subscribe(AnalysisAsyncStatusResponse initialStatus) {
        return sseSubscriptionRegistry.subscribe(
                channelKey(initialStatus.taskId()),
                EVENT_NAME,
                initialStatus,
                isTerminal(initialStatus)
        );
    }

    public void publish(AnalysisAsyncStatusResponse statusResponse) {
        sseSubscriptionRegistry.publish(
                channelKey(statusResponse.taskId()),
                EVENT_NAME,
                statusResponse,
                isTerminal(statusResponse)
        );
    }

    private String channelKey(String taskId) {
        return "analysis:" + taskId;
    }

    private boolean isTerminal(AnalysisAsyncStatusResponse statusResponse) {
        return "SUCCEEDED".equals(statusResponse.status()) || "FAILED".equals(statusResponse.status());
    }
}
