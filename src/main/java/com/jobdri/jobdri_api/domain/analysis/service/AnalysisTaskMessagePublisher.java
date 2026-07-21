package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisTaskMessage;
import com.jobdri.jobdri_api.global.apiPayload.code.BaseErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.logging.LoggingContext;
import com.jobdri.jobdri_api.global.logging.LoggingMdcKeys;
import com.jobdri.jobdri_api.global.logging.WorkerMessageHeaders;
import com.jobdri.jobdri_api.global.mq.service.RabbitPublishSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
// 분석 작업 메시지를 공통 worker exchange로 발행하는 publisher다.
public class AnalysisTaskMessagePublisher {

    private final RabbitPublishSupport rabbitPublishSupport;
    private final DirectExchange workerExchange;
    private final AnalysisQueueProperties analysisQueueProperties;

    public void publish(AnalysisTaskMessage message) {
        Map<String, String> publishContext = publishContext(message);
        try (var ignored = LoggingContext.with("queue.publish.started", null, publishContext)) {
            log.info("Publishing analysis task message to worker queue");
        }
        try {
            rabbitPublishSupport.publish(
                    workerExchange.getName(),
                    analysisQueueProperties.routingKey(),
                    message,
                    message.messageId(),
                    "자소서 분석 작업 메시지 발행에 실패했습니다.",
                    outgoingMessage -> {
                        outgoingMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        outgoingMessage.getMessageProperties().setMessageId(message.messageId());
                        outgoingMessage.getMessageProperties().setHeader(WorkerMessageHeaders.MESSAGE_ID, message.messageId());
                        outgoingMessage.getMessageProperties().setHeader(WorkerMessageHeaders.TASK_ID, message.taskId());
                        outgoingMessage.getMessageProperties().setHeader(WorkerMessageHeaders.TASK_TYPE, message.taskType());
                        outgoingMessage.getMessageProperties().setHeader(WorkerMessageHeaders.RETRY_COUNT, message.retryCount());
                        if (message.requestId() != null && !message.requestId().isBlank()) {
                            outgoingMessage.getMessageProperties().setHeader(WorkerMessageHeaders.REQUEST_ID, message.requestId());
                        }
                        return outgoingMessage;
                    }
            );
            try (var ignored = LoggingContext.with("queue.publish.completed", null, publishContext)) {
                log.info("Published analysis task message to worker queue");
            }
        } catch (RuntimeException e) {
            BaseErrorCode errorCode = e instanceof GeneralException generalException
                    ? generalException.getCode()
                    : GeneralErrorCode.SERVICE_UNAVAILABLE;
            try (var ignored = LoggingContext.with("queue.publish.failed", errorCode, publishContext)) {
                log.warn("Failed to publish analysis task message: {}", e.getMessage());
            }
            throw e;
        }
    }

    private Map<String, String> publishContext(AnalysisTaskMessage message) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put(LoggingMdcKeys.MESSAGE_ID, message.messageId());
        context.put(LoggingMdcKeys.TASK_ID, message.taskId());
        context.put(LoggingMdcKeys.TASK_TYPE, message.taskType());
        context.put(LoggingMdcKeys.RETRY_COUNT, String.valueOf(message.retryCount()));
        if (message.requestId() != null) {
            context.put(LoggingMdcKeys.REQUEST_ID, message.requestId());
        }
        return context;
    }
}
