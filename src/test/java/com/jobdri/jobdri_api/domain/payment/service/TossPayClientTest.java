package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TossPayClientTest {

    @Mock(answer = Answers.RETURNS_SELF)
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    private TossPayClient tossPayClient;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.build()).thenReturn(restClient);
        tossPayClient = new TossPayClient(restClientBuilder);
        ReflectionTestUtils.setField(tossPayClient, "apiBaseUrl", "https://pay.toss.im");
    }

    @Test
    @DisplayName("토스페이 설정이 비어 있어도 초기화는 가능하고 실제 호출에서 예외를 던진다")
    void createPaymentThrowsServiceUnavailableWhenConfigurationIsMissing() {
        ReflectionTestUtils.setField(tossPayClient, "apiKey", "");
        ReflectionTestUtils.setField(tossPayClient, "returnUrl", "");
        ReflectionTestUtils.setField(tossPayClient, "cancelUrl", "");
        ReflectionTestUtils.setField(tossPayClient, "resultCallbackUrl", "");

        tossPayClient.init();

        assertThatThrownBy(() -> tossPayClient.createPayment("order-1", 2500, "JobDri 크레딧"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);
    }
}
