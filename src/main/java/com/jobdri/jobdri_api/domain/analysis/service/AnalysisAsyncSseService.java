package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.TaskStatus;
import com.jobdri.jobdri_api.global.sse.SseSubscriptionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AnalysisAsyncSseService {

    private static final String EVENT_NAME = "analysis-status";

    private final SseSubscriptionRegistry sseSubscriptionRegistry;

    public SseEmitter subscribe(String taskId, Supplier<AnalysisAsyncStatusResponse> initialStatusSupplier) {
        return sseSubscriptionRegistry.subscribe(
                channelKey(taskId),
                EVENT_NAME,
                initialStatusSupplier,
                this::isTerminal
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
        TaskStatus status = TaskStatus.valueOf(statusResponse.status());
        return status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED;
    }
}
