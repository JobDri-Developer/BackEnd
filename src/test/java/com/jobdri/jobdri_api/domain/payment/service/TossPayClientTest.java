package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.tosspay.TossPayStatusResponse;
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
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TossPayClientTest {

    @Mock(answer = Answers.RETURNS_SELF)
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock(answer = Answers.RETURNS_SELF)
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private TossPayClient tossPayClient;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.build()).thenReturn(restClient);
        tossPayClient = new TossPayClient(restClientBuilder);
        ReflectionTestUtils.setField(tossPayClient, "apiBaseUrl", "https://pay.toss.im");
        ReflectionTestUtils.setField(tossPayClient, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(tossPayClient, "returnUrl", "https://jobdri.site/credit/payment-result");
        ReflectionTestUtils.setField(tossPayClient, "cancelUrl", "https://jobdri.site/credit/payment-cancel");
        ReflectionTestUtils.setField(tossPayClient, "resultCallbackUrl", "https://api.jobdri.site/api/payments/toss/callback");
        tossPayClient.init();
    }

    @Test
    @DisplayName("결제 생성은 apiKey 누락 시 SERVICE_UNAVAILABLE를 던진다")
    void createPaymentThrowsServiceUnavailableWhenApiKeyMissing() {
        ReflectionTestUtils.setField(tossPayClient, "apiKey", "");

        assertThatThrownBy(() -> tossPayClient.createPayment("order-1", 2500, "JobDri 크레딧"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("결제 생성은 returnUrl 누락 시 SERVICE_UNAVAILABLE를 던진다")
    void createPaymentThrowsServiceUnavailableWhenReturnUrlMissing() {
        ReflectionTestUtils.setField(tossPayClient, "returnUrl", "");

        assertThatThrownBy(() -> tossPayClient.createPayment("order-1", 2500, "JobDri 크레딧"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("결제 생성은 cancelUrl 누락 시 SERVICE_UNAVAILABLE를 던진다")
    void createPaymentThrowsServiceUnavailableWhenCancelUrlMissing() {
        ReflectionTestUtils.setField(tossPayClient, "cancelUrl", "");

        assertThatThrownBy(() -> tossPayClient.createPayment("order-1", 2500, "JobDri 크레딧"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("결제 생성은 resultCallbackUrl 누락 시 SERVICE_UNAVAILABLE를 던진다")
    void createPaymentThrowsServiceUnavailableWhenResultCallbackUrlMissing() {
        ReflectionTestUtils.setField(tossPayClient, "resultCallbackUrl", "");

        assertThatThrownBy(() -> tossPayClient.createPayment("order-1", 2500, "JobDri 크레딧"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("상태 조회는 callback URL이 비어 있어도 apiKey만 있으면 진행한다")
    void getPaymentStatusDoesNotRequireCallbackUrls() {
        TossPayStatusResponse expected = new TossPayStatusResponse(
                0,
                null,
                "ok",
                "PROD",
                "pay-token-1",
                "order-1",
                "PAY_COMPLETE",
                "CARD",
                2500,
                0,
                2500
        );
        ReflectionTestUtils.setField(tossPayClient, "returnUrl", "");
        ReflectionTestUtils.setField(tossPayClient, "cancelUrl", "");
        ReflectionTestUtils.setField(tossPayClient, "resultCallbackUrl", "");
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/v2/status")).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.body(new com.jobdri.jobdri_api.domain.payment.dto.tosspay.TossPayStatusRequest(
                "test-api-key",
                "pay-token-1",
                "order-1"
        ))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(TossPayStatusResponse.class)).thenReturn(expected);

        TossPayStatusResponse actual = tossPayClient.getPaymentStatus("pay-token-1", "order-1");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("상태 조회는 apiKey 누락 시 SERVICE_UNAVAILABLE를 던진다")
    void getPaymentStatusThrowsServiceUnavailableWhenApiKeyMissing() {
        ReflectionTestUtils.setField(tossPayClient, "apiKey", "");

        assertThatThrownBy(() -> tossPayClient.getPaymentStatus("pay-token-1", "order-1"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("상태 조회는 payToken 누락 시 INVALID_PARAMETER를 던진다")
    void getPaymentStatusThrowsInvalidParameterWhenPayTokenMissing() {
        assertThatThrownBy(() -> tossPayClient.getPaymentStatus("", "order-1"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("상태 조회는 orderNo 누락 시 INVALID_PARAMETER를 던진다")
    void getPaymentStatusThrowsInvalidParameterWhenOrderNoMissing() {
        assertThatThrownBy(() -> tossPayClient.getPaymentStatus("pay-token-1", ""))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }
}
