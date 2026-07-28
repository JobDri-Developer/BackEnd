package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.toss.TossPaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.toss.TossPaymentConfirmResponse;
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

import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TossPaymentClient {

    private static final int LOG_MESSAGE_MAX_LENGTH = 500;

    private final RestClient.Builder restClientBuilder;
    private RestClient restClient;

    @Value("${payment.toss.secret-key}")
    private String secretKey;

    @Value("${payment.toss.base-url:https://api.tosspayments.com}")
    private String baseUrl;

    @PostConstruct
    void init() {
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
        Map<String, String> paymentContext = PaymentLogMasking.paymentContext(orderId, paymentKey, amount);
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
            try (var ignored = LoggingContext.with("payment.confirm.failed", GeneralErrorCode.PAYMENT_CONFIRM_FAILED, paymentContext)) {
                log.warn(
                        "Toss payment confirm failed. status={}, response={}",
                        e.getStatusCode(),
                        truncate(e.getResponseBodyAsString())
                );
                log.warn("Toss payment confirm exception", e);
            }
            throw new GeneralException(
                    GeneralErrorCode.PAYMENT_CONFIRM_FAILED,
                    "토스페이먼츠 결제 승인 실패",
                    e
            );
        } catch (ResourceAccessException e) {
            if (isTimeoutException(e)) {
                try (var ignored = LoggingContext.with("payment.confirm.external_timeout", GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, paymentContext)) {
                    log.warn("Toss payment confirm request timed out. message={}", truncate(e.getMessage()));
                    log.warn("Toss payment confirm timeout exception", e);
                }
                throw new GeneralException(
                        GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT,
                        "토스페이먼츠 결제 승인 응답이 지연되고 있습니다.",
                        e
                );
            }
            try (var ignored = LoggingContext.with("payment.confirm.failed", GeneralErrorCode.PAYMENT_CONFIRM_FAILED, paymentContext)) {
                log.warn("Toss payment confirm request failed. message={}", truncate(e.getMessage()));
                log.warn("Toss payment confirm request exception", e);
            }
            throw new GeneralException(
                    GeneralErrorCode.PAYMENT_CONFIRM_FAILED,
                    "토스페이먼츠 결제 승인 중 오류 발생",
                    e
            );
        } catch (RestClientException e) {
            try (var ignored = LoggingContext.with("payment.confirm.failed", GeneralErrorCode.PAYMENT_CONFIRM_FAILED, paymentContext)) {
                log.warn("Toss payment confirm request failed. message={}", truncate(e.getMessage()));
                log.warn("Toss payment confirm request exception", e);
            }
            throw new GeneralException(
                    GeneralErrorCode.PAYMENT_CONFIRM_FAILED,
                    "토스페이먼츠 결제 승인 중 오류 발생",
                    e
            );
        }
    }

    private String authorizationHeader() {
        String credential = secretKey + ":";
        return "Basic " + Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
    }

    private String truncate(String value) {
        if (value == null || value.length() <= LOG_MESSAGE_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, LOG_MESSAGE_MAX_LENGTH) + "...";
    }

    private boolean isTimeoutException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof InterruptedIOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
