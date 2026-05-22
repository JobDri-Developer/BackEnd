package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.toss.TossPaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.toss.TossPaymentConfirmResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class TossPaymentClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${payment.toss.secret-key:}")
    private String secretKey;

    @Value("${payment.toss.base-url:https://api.tosspayments.com}")
    private String baseUrl;

    public TossPaymentConfirmResponse confirm(String paymentKey, String orderId, int amount) {
        try {
            return restClientBuilder
                    .baseUrl(baseUrl)
                    .build()
                    .post()
                    .uri("/v1/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
                    .header("Idempotency-Key", orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossPaymentConfirmRequest(paymentKey, orderId, amount))
                    .retrieve()
                    .body(TossPaymentConfirmResponse.class);
        } catch (RestClientException e) {
            throw new GeneralException(
                    GeneralErrorCode.PAYMENT_CONFIRM_FAILED,
                    "토스페이먼츠 결제 승인에 실패했습니다."
            );
        }
    }

    private String authorizationHeader() {
        String credential = secretKey + ":";
        return "Basic " + Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
    }
}
