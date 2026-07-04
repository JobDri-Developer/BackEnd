package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisTaskMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalysisTaskMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.worker.analysis.exchange:jobdri.worker.exchange}")
    private String exchange;

    @Value("${app.worker.analysis.routing-key:analysis.execute}")
    private String routingKey;

    public void publish(AnalysisTaskMessage message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message, outgoingMessage -> {
            outgoingMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            outgoingMessage.getMessageProperties().setHeader("x-task-id", message.taskId());
            outgoingMessage.getMessageProperties().setHeader("x-task-type", message.taskType());
            outgoingMessage.getMessageProperties().setHeader("x-retry-count", message.retryCount());
            return outgoingMessage;
        });
    }
}
