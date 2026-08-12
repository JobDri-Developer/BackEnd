package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.external.portone.PortOnePaymentResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortOneClientTest {

    @Mock(answer = Answers.RETURNS_SELF)
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    private PortOneClient portOneClient;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.build()).thenReturn(restClient);
        portOneClient = new PortOneClient(restClientBuilder);
        ReflectionTestUtils.setField(portOneClient, "apiBaseUrl", "http://localhost:18080");
    }

    @Test
    @DisplayName("PORTONE_ENABLED=false이면 포트원 키가 없어도 초기화된다")
    void initDoesNotFailWhenDisabledAndKeysMissing() {
        ReflectionTestUtils.setField(portOneClient, "enabled", false);
        ReflectionTestUtils.setField(portOneClient, "storeId", "");
        ReflectionTestUtils.setField(portOneClient, "channelKey", "");
        ReflectionTestUtils.setField(portOneClient, "apiSecret", "");
        ReflectionTestUtils.setField(portOneClient, "redirectUrl", "");

        assertThatCode(() -> portOneClient.init()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PORTONE_ENABLED=true이면 필수 설정 누락 시 초기화에 실패한다")
    void initFailsWhenEnabledAndRequiredConfigMissing() {
        ReflectionTestUtils.setField(portOneClient, "enabled", true);
        ReflectionTestUtils.setField(portOneClient, "storeId", "");
        ReflectionTestUtils.setField(portOneClient, "channelKey", "");
        ReflectionTestUtils.setField(portOneClient, "apiSecret", "");
        ReflectionTestUtils.setField(portOneClient, "redirectUrl", "");

        assertThatThrownBy(() -> portOneClient.init())
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("포트원 결제 조회 5xx는 재시도 후 성공 응답을 반환한다")
    void getPaymentRetriesServerError() {
        enableClient();
        PortOnePaymentResponse response = new PortOnePaymentResponse(
                "jobdri-order",
                "transaction-id",
                "PAID",
                "store-test",
                "KRW",
                null
        );
        stubGetPaymentBody("jobdri-order")
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8
                ))
                .thenReturn(response);

        assertThat(portOneClient.getPayment("jobdri-order")).isEqualTo(response);
    }

    @Test
    @DisplayName("포트원 결제 조회 timeout은 재시도 후 EXTERNAL_SERVICE_TIMEOUT으로 매핑한다")
    void getPaymentMapsTimeoutAfterRetries() {
        enableClient();
        stubGetPaymentBody("jobdri-timeout")
                .thenThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> portOneClient.getPayment("jobdri-timeout"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT);
    }

    @Test
    @DisplayName("포트원 결제 조회 중 일반 RestClientException은 SERVICE_UNAVAILABLE로 매핑한다")
    void getPaymentMapsRestClientException() {
        enableClient();
        stubGetPaymentBody("jobdri-error")
                .thenThrow(new RestClientException("connection failed"));

        assertThatThrownBy(() -> portOneClient.getPayment("jobdri-error"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("포트원 결제 조회 paymentId가 비어 있으면 INVALID_PARAMETER로 검증한다")
    void getPaymentRejectsBlankPaymentId() {
        enableClient();

        assertThatThrownBy(() -> portOneClient.getPayment(" "))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("포트원 결제 조회 응답 body가 비어 있으면 PAYMENT_CONFIRM_FAILED로 처리한다")
    void getPaymentRejectsNullBody() {
        enableClient();
        stubGetPaymentBody("jobdri-empty")
                .thenReturn(null);

        assertThatThrownBy(() -> portOneClient.getPayment("jobdri-empty"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.PAYMENT_CONFIRM_FAILED);
    }

    private void enableClient() {
        ReflectionTestUtils.setField(portOneClient, "enabled", true);
        ReflectionTestUtils.setField(portOneClient, "storeId", "store-test");
        ReflectionTestUtils.setField(portOneClient, "channelKey", "channel-key-test");
        ReflectionTestUtils.setField(portOneClient, "apiSecret", "test-secret");
        ReflectionTestUtils.setField(portOneClient, "redirectUrl", "http://localhost:3000/credit/payment-result");
        portOneClient.init();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private OngoingStubbing<PortOnePaymentResponse> stubGetPaymentBody(String paymentId) {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri("/payments/{paymentId}", paymentId)).thenReturn(headersSpec);
        when(headersSpec.header(HttpHeaders.AUTHORIZATION, "PortOne test-secret")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        return when(responseSpec.body(PortOnePaymentResponse.class));
    }
}
