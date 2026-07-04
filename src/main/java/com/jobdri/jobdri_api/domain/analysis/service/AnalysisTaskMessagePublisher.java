package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisTaskMessage;
import com.jobdri.jobdri_api.global.mq.service.RabbitPublishSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalysisTaskMessagePublisher {

    private final RabbitPublishSupport rabbitPublishSupport;
    private final DirectExchange workerExchange;
    private final AnalysisQueueProperties analysisQueueProperties;

    public void publish(AnalysisTaskMessage message) {
        rabbitPublishSupport.publish(
                workerExchange.getName(),
                analysisQueueProperties.routingKey(),
                message,
                message.messageId(),
                "자소서 분석 작업 메시지 발행에 실패했습니다.",
                outgoingMessage -> {
                    outgoingMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    outgoingMessage.getMessageProperties().setMessageId(message.messageId());
                    outgoingMessage.getMessageProperties().setHeader("x-task-id", message.taskId());
                    outgoingMessage.getMessageProperties().setHeader("x-task-type", message.taskType());
                    outgoingMessage.getMessageProperties().setHeader("x-retry-count", message.retryCount());
                    return outgoingMessage;
                }
        );
    }
}
