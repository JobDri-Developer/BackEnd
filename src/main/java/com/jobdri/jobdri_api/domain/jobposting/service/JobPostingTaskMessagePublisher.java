package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingIngestTaskMessage;
import com.jobdri.jobdri_api.global.mq.service.RabbitPublishSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobPostingTaskMessagePublisher {

    private final RabbitPublishSupport rabbitPublishSupport;

    @Value("${app.worker.job-posting.exchange:jobdri.worker.exchange}")
    private String exchange;

    @Value("${app.worker.job-posting.routing-key:job-posting.ingest}")
    private String routingKey;

    public void publish(JobPostingIngestTaskMessage message) {
        rabbitPublishSupport.publish(
                exchange,
                routingKey,
                message,
                message.messageId(),
                "채용 공고 작업 메시지 발행에 실패했습니다.",
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
