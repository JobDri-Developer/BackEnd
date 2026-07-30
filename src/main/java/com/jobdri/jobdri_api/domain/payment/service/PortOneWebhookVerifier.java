package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Component
@Slf4j
public class PortOneWebhookVerifier {

    private static final long TIMESTAMP_TOLERANCE_SECONDS = 300;

    @Value("${payment.portone.enabled:false}")
    private boolean enabled;

    @Value("${payment.portone.webhook-secret:}")
    private String webhookSecret;

    public void verify(String rawBody, HttpHeaders headers) {
        if (!enabled) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, "포트원 결제가 비활성화되어 있습니다.");
        }
        if (!StringUtils.hasText(webhookSecret)) {
            log.warn("PortOne webhook verification failed because payment.portone.webhook-secret is not configured");
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, "포트원 웹훅 시크릿이 설정되지 않았습니다.");
        }
        String webhookId = firstHeader(headers, "webhook-id");
        String webhookTimestamp = firstHeader(headers, "webhook-timestamp");
        String webhookSignature = firstHeader(headers, "webhook-signature");
        if (!StringUtils.hasText(webhookId)
                || !StringUtils.hasText(webhookTimestamp)
                || !StringUtils.hasText(webhookSignature)) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "포트원 웹훅 서명 헤더가 누락되었습니다.");
        }
        validateTimestamp(webhookTimestamp);
        if (!matchesSignature(webhookId, webhookTimestamp, rawBody, webhookSignature)) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "포트원 웹훅 서명 검증에 실패했습니다.");
        }
    }

    private String firstHeader(HttpHeaders headers, String name) {
        List<String> values = headers.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }

    private void validateTimestamp(String webhookTimestamp) {
        try {
            long timestamp = Long.parseLong(webhookTimestamp);
            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - timestamp) > TIMESTAMP_TOLERANCE_SECONDS) {
                throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "포트원 웹훅 타임스탬프가 허용 범위를 벗어났습니다.");
            }
        } catch (NumberFormatException e) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "포트원 웹훅 타임스탬프 형식이 잘못되었습니다.", e);
        }
    }

    private boolean matchesSignature(String webhookId, String timestamp, String rawBody, String signatureHeader) {
        byte[] expected = sign(webhookId + "." + timestamp + "." + rawBody);
        for (String signature : signatureHeader.split(" ")) {
            if (!signature.startsWith("v1,")) {
                continue;
            }
            byte[] provided = decodeBase64(signature.substring(3));
            if (provided != null && MessageDigest.isEqual(expected, provided)) {
                return true;
            }
        }
        return false;
    }

    private byte[] sign(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes(), "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR, "포트원 웹훅 서명 검증 중 오류가 발생했습니다.", e);
        }
    }

    private byte[] secretBytes() {
        if (webhookSecret.startsWith("whsec_")) {
            byte[] decoded = decodeBase64(webhookSecret.substring("whsec_".length()));
            if (decoded == null) {
                throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "포트원 웹훅 시크릿 형식이 잘못되었습니다.");
            }
            return decoded;
        }
        return webhookSecret.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
