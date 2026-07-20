package com.jobdri.jobdri_api.global.mq.service;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class RabbitPublishSupportTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private RabbitPublishSupport rabbitPublishSupport;

    @Test
    @DisplayName("메시지 발행 직전 RabbitMQ 연결 예외가 발생하면 SERVICE_UNAVAILABLE로 변환한다")
    void publishWrapsAmqpExceptionAsServiceUnavailable() {
        ReflectionTestUtils.setField(rabbitPublishSupport, "publishConfirmTimeoutMillis", 1000L);

        AmqpConnectException cause = new AmqpConnectException(new RuntimeException("rabbitmq unavailable"));
        doThrow(cause).when(rabbitTemplate)
                .convertAndSend(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );

        assertThatThrownBy(() -> rabbitPublishSupport.publish(
                "worker.exchange",
                "analysis.execute",
                "payload",
                "correlation-id",
                "자소서 분석 작업 메시지 발행에 실패했습니다.",
                outgoingMessage -> outgoingMessage
        ))
                .isInstanceOf(GeneralException.class)
                .satisfies(exception -> {
                    GeneralException generalException = (GeneralException) exception;
                    assertThat(generalException.getCode()).isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);
                    assertThat(generalException.getCause()).isEqualTo(cause);
                });
    }
}
