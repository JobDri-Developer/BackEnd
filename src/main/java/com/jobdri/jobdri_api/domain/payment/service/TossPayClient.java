package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.tosspay.TossPayCreateRequest;
import com.jobdri.jobdri_api.domain.payment.dto.tosspay.TossPayCreateResponse;
import com.jobdri.jobdri_api.domain.payment.dto.tosspay.TossPayStatusRequest;
import com.jobdri.jobdri_api.domain.payment.dto.tosspay.TossPayStatusResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.logging.LoggingContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TossPayClient {

    private final RestClient.Builder restClientBuilder;
    private RestClient restClient;

    @Value("${payment.toss-pay.api-key:}")
    private String apiKey;

    @Value("${payment.toss-pay.api-base-url:https://pay.toss.im}")
    private String apiBaseUrl;

    @Value("${payment.toss-pay.return-url:}")
    private String returnUrl;

    @Value("${payment.toss-pay.cancel-url:}")
    private String cancelUrl;

    @Value("${payment.toss-pay.result-callback-url:}")
    private String resultCallbackUrl;

    @PostConstruct
    void init() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("payment.toss-pay.api-key must be configured");
        }
        if (returnUrl == null || returnUrl.isBlank()) {
            throw new IllegalStateException("payment.toss-pay.return-url must be configured");
        }
        if (cancelUrl == null || cancelUrl.isBlank()) {
            throw new IllegalStateException("payment.toss-pay.cancel-url must be configured");
        }
        if (resultCallbackUrl == null || resultCallbackUrl.isBlank()) {
            throw new IllegalStateException("payment.toss-pay.result-callback-url must be configured");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = restClientBuilder
                .baseUrl(apiBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public TossPayCreateResponse createPayment(String orderNo, int amount, String productDesc) {
        Map<String, String> paymentContext = PaymentLogMasking.paymentContext(orderNo, null, amount);
        try (var ignored = LoggingContext.with("payment.create.external_called", null, paymentContext)) {
            log.info("Calling Toss Pay create payment API");
        }
        try {
            TossPayCreateResponse response = restClient
                    .post()
                    .uri("/api/v2/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TossPayCreateRequest.of(
                            orderNo,
                            amount,
                            productDesc,
                            apiKey,
                            returnUrl,
                            cancelUrl,
                            resultCallbackUrl
                    ))
                    .retrieve()
                    .body(TossPayCreateResponse.class);
            if (response == null || !response.successful()) {
                throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "토스페이 결제 생성 응답 검증에 실패했습니다.");
            }
            try (var ignored = LoggingContext.with("payment.create.external_succeeded", null, paymentContext)) {
                log.info("Toss Pay create payment API succeeded");
            }
            return response;
        } catch (GeneralException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            try (var ignored = LoggingContext.with("payment.create.failed", GeneralErrorCode.PAYMENT_CONFIRM_FAILED, paymentContext)) {
                log.warn("Toss Pay create payment failed. status={}, response={}",
                        e.getStatusCode(),
                        TossHttpClientSupport.truncate(e.getResponseBodyAsString()));
                log.warn("Toss Pay create payment exception", e);
            }
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "토스페이 결제 생성 실패", e);
        } catch (ResourceAccessException e) {
            if (TossHttpClientSupport.isTimeoutException(e)) {
                try (var ignored = LoggingContext.with("payment.create.external_timeout", GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, paymentContext)) {
                    log.warn("Toss Pay create payment request timed out. message={}", TossHttpClientSupport.truncate(e.getMessage()));
                    log.warn("Toss Pay create payment timeout exception", e);
                }
                throw new GeneralException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, "토스페이 결제 생성 응답이 지연되고 있습니다.", e);
            }
            throwPaymentCreateFailure(e, paymentContext);
            return null;
        } catch (RestClientException e) {
            throwPaymentCreateFailure(e, paymentContext);
            return null;
        }
    }

    public TossPayStatusResponse getPaymentStatus(String payToken, String orderNo) {
        try {
            return restClient
                    .post()
                    .uri("/api/v2/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossPayStatusRequest(apiKey, payToken, orderNo))
                    .retrieve()
                    .body(TossPayStatusResponse.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is5xxServerError()) {
                throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, "토스페이 결제 상태 조회가 일시적으로 실패했습니다.", e);
            }
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "토스페이 결제 상태 조회 실패", e);
        } catch (ResourceAccessException e) {
            if (TossHttpClientSupport.isTimeoutException(e)) {
                throw new GeneralException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, "토스페이 결제 상태 조회 응답이 지연되고 있습니다.", e);
            }
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, "토스페이 결제 상태 조회 중 통신 오류가 발생했습니다.", e);
        } catch (RestClientException e) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, "토스페이 결제 상태 조회 중 오류가 발생했습니다.", e);
        }
    }

    private void throwPaymentCreateFailure(RestClientException e, Map<String, String> paymentContext) {
        try (var ignored = LoggingContext.with("payment.create.failed", GeneralErrorCode.PAYMENT_CONFIRM_FAILED, paymentContext)) {
            log.warn("Toss Pay create payment request failed. message={}", TossHttpClientSupport.truncate(e.getMessage()));
            log.warn("Toss Pay create payment request exception", e);
        }
        throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "토스페이 결제 생성 중 오류 발생", e);
    }
}
