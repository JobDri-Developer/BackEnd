package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.toss.TossPaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.toss.TossPaymentConfirmResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Component
@RequiredArgsConstructor
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
        try {
            return restClient
                    .post()
                    .uri("/v1/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
                    .header("Idempotency-Key", orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossPaymentConfirmRequest(paymentKey, orderId, amount))
                    .retrieve()
                    .body(TossPaymentConfirmResponse.class);
        } catch (HttpStatusCodeException e) {
            throw new GeneralException(
                    GeneralErrorCode.PAYMENT_CONFIRM_FAILED,
                    "토스페이먼츠 결제 승인 실패: " + e.getStatusCode() + " - " + e.getResponseBodyAsString()
            );
        } catch (RestClientException e) {
            throw new GeneralException(
                    GeneralErrorCode.PAYMENT_CONFIRM_FAILED,
                    "토스페이먼츠 결제 승인 중 오류 발생: " + e.getMessage()
            );
        }
    }

    private String authorizationHeader() {
        String credential = secretKey + ":";
        return "Basic " + Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
    }
}
