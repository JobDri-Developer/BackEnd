package com.jobdri.jobdri_api.global.mq.service;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class RabbitPublishSupport {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.worker.publish-confirm-timeout-millis:5000}")
    private long publishConfirmTimeoutMillis;

    public void publish(
            String exchange,
            String routingKey,
            Object payload,
            String correlationId,
            String failureMessage,
            MessagePostProcessor messagePostProcessor
    ) {
        CorrelationData correlationData = new CorrelationData(correlationId);
        rabbitTemplate.convertAndSend(exchange, routingKey, payload, messagePostProcessor, correlationData);
        awaitPublisherConfirm(correlationData, failureMessage);
    }

    private void awaitPublisherConfirm(CorrelationData correlationData, String failureMessage) {
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(publishConfirmTimeoutMillis, TimeUnit.MILLISECONDS);

            if (confirm == null || !confirm.isAck()) {
                throw new GeneralException(
                        GeneralErrorCode.SERVICE_UNAVAILABLE,
                        failureMessage + buildReasonSuffix(confirm != null ? confirm.getReason() : null)
                );
            }

            ReturnedMessage returnedMessage = correlationData.getReturned();
            if (returnedMessage != null) {
                throw new GeneralException(
                        GeneralErrorCode.SERVICE_UNAVAILABLE,
                        failureMessage + " routingKey=" + returnedMessage.getRoutingKey()
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw withCause(failureMessage, e);
        } catch (ExecutionException | TimeoutException e) {
            throw withCause(failureMessage, e);
        }
    }

    private String buildReasonSuffix(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        return " reason=" + reason;
    }

    private GeneralException withCause(String failureMessage, Exception cause) {
        GeneralException exception = new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, failureMessage);
        exception.initCause(cause);
        return exception;
    }
}
