package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.portone.PortOnePaymentResponse;
import com.jobdri.jobdri_api.domain.payment.dto.portone.PortOnePrepareData;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.logging.LoggingContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PortOneClient {

    private final RestClient.Builder restClientBuilder;
    private RestClient restClient;

    @Value("${payment.portone.enabled:false}")
    private boolean enabled;

    @Value("${payment.portone.api-base-url:https://api.portone.io}")
    private String apiBaseUrl;

    @Value("${payment.portone.store-id:}")
    private String storeId;

    @Value("${payment.portone.channel-key:}")
    private String channelKey;

    @Value("${payment.portone.api-secret:}")
    private String apiSecret;

    @Value("${payment.portone.redirect-url:}")
    private String redirectUrl;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = restClientBuilder
                .baseUrl(apiBaseUrl)
                .requestFactory(requestFactory)
                .build();

        if (enabled) {
            ensureCreatePaymentConfigured();
        }
    }

    public PortOnePrepareData prepareData() {
        ensureCreatePaymentConfigured();
        return new PortOnePrepareData(storeId, channelKey, redirectUrl);
    }

    public String storeId() {
        ensureConfigured();
        return storeId;
    }

    public PortOnePaymentResponse getPayment(String paymentId) {
        ensureConfigured();
        ensureRequestValue(paymentId, "paymentId");
        Map<String, String> paymentContext = PaymentLogMasking.paymentContext(paymentId, null, null);
        try (var ignored = LoggingContext.with("payment.portone.status.external_called", null, paymentContext)) {
            log.info("Calling PortOne get payment API");
        }
        try {
            return restClient
                    .get()
                    .uri("/payments/{paymentId}", paymentId)
                    .header(HttpHeaders.AUTHORIZATION, "PortOne " + apiSecret)
                    .retrieve()
                    .body(PortOnePaymentResponse.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is5xxServerError()) {
                throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, "포트원 결제 단건 조회가 일시적으로 실패했습니다.", e);
            }
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "포트원 결제 단건 조회 실패", e);
        } catch (ResourceAccessException e) {
            if (TossHttpClientSupport.isTimeoutException(e)) {
                throw new GeneralException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, "포트원 결제 단건 조회 응답이 지연되고 있습니다.", e);
            }
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, "포트원 결제 단건 조회 중 통신 오류가 발생했습니다.", e);
        } catch (RestClientException e) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, "포트원 결제 단건 조회 중 오류가 발생했습니다.", e);
        }
    }

    private void ensureCreatePaymentConfigured() {
        ensureConfigured();
        ensureConfiguredValue(channelKey, "payment.portone.channel-key");
        ensureConfiguredValue(redirectUrl, "payment.portone.redirect-url");
    }

    private void ensureConfigured() {
        if (!enabled) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, "포트원 결제가 비활성화되어 있습니다.");
        }
        ensureConfiguredValue(storeId, "payment.portone.store-id");
        ensureConfiguredValue(apiSecret, "payment.portone.api-secret");
    }

    private void ensureConfiguredValue(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            log.warn("PortOne integration is unavailable because {} is not configured", propertyName);
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "포트원 설정이 누락되어 결제를 진행할 수 없습니다. (" + propertyName + ")"
            );
        }
    }

    private void ensureRequestValue(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, fieldName + "는 필수입니다.");
        }
    }
}
