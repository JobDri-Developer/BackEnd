package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingIngestTaskMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobPostingTaskMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.worker.job-posting.exchange:jobdri.worker.exchange}")
    private String exchange;

    @Value("${app.worker.job-posting.routing-key:job-posting.ingest}")
    private String routingKey;

    public void publish(JobPostingIngestTaskMessage message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message, outgoingMessage -> {
            outgoingMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            outgoingMessage.getMessageProperties().setHeader("x-task-id", message.taskId());
            outgoingMessage.getMessageProperties().setHeader("x-task-type", message.taskType());
            outgoingMessage.getMessageProperties().setHeader("x-retry-count", message.retryCount());
            return outgoingMessage;
        });
    }
}
