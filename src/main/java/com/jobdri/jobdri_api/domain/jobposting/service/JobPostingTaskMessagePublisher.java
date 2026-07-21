package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingIngestTaskMessage;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.logging.LoggingContext;
import com.jobdri.jobdri_api.global.logging.LoggingMdcKeys;
import com.jobdri.jobdri_api.global.logging.WorkerMessageHeaders;
import com.jobdri.jobdri_api.global.mq.service.RabbitPublishSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobPostingTaskMessagePublisher {

    private final RabbitPublishSupport rabbitPublishSupport;
    private final DirectExchange workerExchange;
    private final JobPostingQueueProperties jobPostingQueueProperties;

    public void publish(JobPostingIngestTaskMessage message) {
        Map<String, String> publishContext = publishContext(message);
        try (var ignored = LoggingContext.with("queue.publish.started", null, publishContext)) {
            log.info("Publishing job posting task message to worker queue");
        }
        try {
            rabbitPublishSupport.publish(
                    workerExchange.getName(),
                    jobPostingQueueProperties.routingKey(),
                    message,
                    message.messageId(),
                    "채용 공고 작업 메시지 발행에 실패했습니다.",
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
                log.info("Published job posting task message to worker queue");
            }
        } catch (RuntimeException e) {
            GeneralErrorCode errorCode = e instanceof GeneralException generalException
                    ? (GeneralErrorCode) generalException.getCode()
                    : GeneralErrorCode.SERVICE_UNAVAILABLE;
            try (var ignored = LoggingContext.with("queue.publish.failed", errorCode, publishContext)) {
                log.warn("Failed to publish job posting task message: {}", e.getMessage());
            }
            throw e;
        }
    }

    private Map<String, String> publishContext(JobPostingIngestTaskMessage message) {
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
