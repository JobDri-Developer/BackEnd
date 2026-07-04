package com.jobdri.jobdri_api.global.mq.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestCommand;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingIngestTaskMessage;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.rabbitmq.host=localhost",
        "spring.rabbitmq.port=5672",
        "spring.rabbitmq.username=guest",
        "spring.rabbitmq.password=guest",
        "spring.rabbitmq.publisher-confirm-type=correlated",
        "spring.rabbitmq.publisher-returns=true",
        "spring.rabbitmq.template.mandatory=true"
})
class RabbitPublishSupportIntegrationTest {

    @Autowired
    private RabbitPublishSupport rabbitPublishSupport;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private DirectExchange workerExchange;

    @Value("${app.worker.job-posting.queue:jobdri.job-posting.ingest}")
    private String queueName;

    @Value("${app.worker.job-posting.routing-key:job-posting.ingest}")
    private String routingKey;

    @BeforeEach
    void purgeQueue() {
        rabbitAdmin.purgeQueue(queueName, true);
    }

    @Test
    @DisplayName("RabbitMQ publisher confirm이 오면 메시지가 큐에 적재된다")
    void publishStoresMessageInQueue() {
        JobPostingIngestTaskMessage message = JobPostingIngestTaskMessage.of(
                "task-1",
                JobPostingIngestCommand.builder()
                        .userId(1L)
                        .rawText("채용 공고")
                        .build(),
                3
        );

        rabbitPublishSupport.publish(
                workerExchange.getName(),
                routingKey,
                message,
                message.messageId(),
                "메시지 발행 실패",
                outgoingMessage -> {
                    outgoingMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    outgoingMessage.getMessageProperties().setMessageId(message.messageId());
                    return outgoingMessage;
                }
        );

        Message queuedMessage = rabbitTemplate.receive(queueName, 3000);

        assertThat(queuedMessage).isNotNull();
        assertThat(queuedMessage.getMessageProperties().getMessageId()).isEqualTo(message.messageId());
    }

    @Test
    @DisplayName("routing key가 잘못되면 publisher return을 감지해 예외를 던진다")
    void publishThrowsWhenRouteIsInvalid() {
        JobPostingIngestTaskMessage message = JobPostingIngestTaskMessage.of(
                "task-2",
                JobPostingIngestCommand.builder()
                        .userId(1L)
                        .rawText("채용 공고")
                        .build(),
                3
        );

        assertThatThrownBy(() -> rabbitPublishSupport.publish(
                workerExchange.getName(),
                "job-posting.ingest.invalid",
                message,
                message.messageId(),
                "메시지 발행 실패",
                outgoingMessage -> outgoingMessage
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);
    }
}
