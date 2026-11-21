package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.external.toss.TossPaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.external.toss.TossPaymentConfirmResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.logging.LoggingContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TossPaymentClient {

    private final RestClient.Builder restClientBuilder;
    private RestClient restClient;

    @Value("${payment.toss.secret-key}")
    private String secretKey;

    @Value("${payment.toss.base-url:https://api.tosspayments.com}")
    private String baseUrl;

    @PostConstruct
    void init() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("payment.toss.secret-key must be configured");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public TossPaymentConfirmResponse confirm(String paymentKey, String orderId, int amount) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "토스페이먼츠 시크릿 키가 설정되지 않았습니다.");
        }
        Map<String, String> paymentContext = PaymentLogMasking.paymentContext(orderId, amount);
        try (var ignored = LoggingContext.with("payment.confirm.external_called", null, paymentContext)) {
            log.info("Calling Toss payment confirm API");
        }
        try {
            TossPaymentConfirmResponse response = restClient
                    .post()
                    .uri("/v1/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
                    .header("Idempotency-Key", orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossPaymentConfirmRequest(paymentKey, orderId, amount))
                    .retrieve()
                    .body(TossPaymentConfirmResponse.class);
            try (var ignored = LoggingContext.with("payment.confirm.external_succeeded", null, paymentContext)) {
                log.info("Toss payment confirm API succeeded");
            }
            return response;
        } catch (HttpStatusCodeException e) {
            // 4xx는 토스가 승인을 거절한 확정 실패, 5xx는 승인 여부를 알 수 없는 상태다.
            GeneralErrorCode errorCode = e.getStatusCode().is5xxServerError()
                    ? GeneralErrorCode.PAYMENT_CONFIRM_UNKNOWN
                    : GeneralErrorCode.PAYMENT_CONFIRM_FAILED;
            try (var ignored = LoggingContext.with("payment.confirm.failed", errorCode, paymentContext)) {
                log.warn(
                        "Toss payment confirm failed. status={}, response={}",
                        e.getStatusCode(),
                        TossHttpClientSupport.truncate(e.getResponseBodyAsString())
                );
                log.warn("Toss payment confirm exception", e);
            }
            throw new GeneralException(
                    errorCode,
                    "토스페이먼츠 결제 승인 실패",
                    e
            );
        } catch (ResourceAccessException e) {
            if (TossHttpClientSupport.isTimeoutException(e)) {
                try (var ignored = LoggingContext.with("payment.confirm.external_timeout", GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, paymentContext)) {
                    log.warn("Toss payment confirm request timed out. message={}", TossHttpClientSupport.truncate(e.getMessage()));
                    log.warn("Toss payment confirm timeout exception", e);
                }
                throw new GeneralException(
                        GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT,
                        "토스페이먼츠 결제 승인 응답이 지연되고 있습니다.",
                        e
                );
            }
            try (var ignored = LoggingContext.with("payment.confirm.unknown", GeneralErrorCode.PAYMENT_CONFIRM_UNKNOWN, paymentContext)) {
                log.warn("Toss payment confirm request failed. message={}", TossHttpClientSupport.truncate(e.getMessage()));
                log.warn("Toss payment confirm request exception", e);
            }
            throw new GeneralException(
                    GeneralErrorCode.PAYMENT_CONFIRM_UNKNOWN,
                    "토스페이먼츠 결제 승인 중 오류 발생",
                    e
            );
        } catch (RestClientException e) {
            // 응답 해석 실패 등은 토스에서 이미 승인됐을 수 있으므로 결과 불명으로 본다.
            try (var ignored = LoggingContext.with("payment.confirm.unknown", GeneralErrorCode.PAYMENT_CONFIRM_UNKNOWN, paymentContext)) {
                log.warn("Toss payment confirm request failed. message={}", TossHttpClientSupport.truncate(e.getMessage()));
                log.warn("Toss payment confirm request exception", e);
            }
            throw new GeneralException(
                    GeneralErrorCode.PAYMENT_CONFIRM_UNKNOWN,
                    "토스페이먼츠 결제 승인 중 오류 발생",
                    e
            );
        }
    }

    /**
     * 승인은 됐지만 서비스에서 받을 수 없는 결제를 전액 취소한다.
     * 같은 주문의 취소 요청은 멱등 키로 한 번만 처리된다.
     */
    public void cancel(String paymentKey, String orderId, String cancelReason) {
        Map<String, String> paymentContext = PaymentLogMasking.paymentContext(orderId, null);
        try {
            restClient
                    .post()
                    .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
                    .header("Idempotency-Key", "cancel-" + orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cancelReason", cancelReason))
                    .retrieve()
                    .toBodilessEntity();
            try (var ignored = LoggingContext.with("payment.confirm.auto_cancel_succeeded", null, paymentContext)) {
                log.info("Toss payment auto cancel succeeded");
            }
        } catch (RestClientException e) {
            try (var ignored = LoggingContext.with("payment.confirm.auto_cancel_failed", GeneralErrorCode.PAYMENT_REFUND_FAILED, paymentContext)) {
                log.warn("Toss payment auto cancel failed. message={}", TossHttpClientSupport.truncate(e.getMessage()));
            }
            throw new GeneralException(GeneralErrorCode.PAYMENT_REFUND_FAILED, "토스페이먼츠 결제 취소 실패", e);
        }
    }

    private String authorizationHeader() {
        String credential = secretKey + ":";
        return "Basic " + Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
    }
}
