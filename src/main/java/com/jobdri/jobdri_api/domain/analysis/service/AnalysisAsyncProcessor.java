package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisTaskMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalysisAsyncProcessor {

    private final AnalysisTaskMessagePublisher analysisTaskMessagePublisher;

    public void process(String taskId, Long userId, Long mockApplyId, String creditReferenceId, int maxRetryCount) {
        analysisTaskMessagePublisher.publish(
                AnalysisTaskMessage.of(taskId, userId, mockApplyId, creditReferenceId, maxRetryCount)
        );
    }
}
