package com.jobdri.jobdri_api.global.security;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalApiKeyValidator {

    private final String configuredApiKey;

    public InternalApiKeyValidator(
            @Value("${app.worker.internal-api-key:change-me-internal-worker-key}") String configuredApiKey
    ) {
        this.configuredApiKey = configuredApiKey;
    }

    public void validate(String providedApiKey) {
        if (!StringUtils.hasText(providedApiKey) || !StringUtils.hasText(configuredApiKey)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "내부 worker 인증에 실패했습니다.");
        }

        boolean matches = MessageDigest.isEqual(
                configuredApiKey.getBytes(StandardCharsets.UTF_8),
                providedApiKey.getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "내부 worker 인증에 실패했습니다.");
        }
    }
}
