package com.jobdri.jobdri_api.global.logging;

import com.jobdri.jobdri_api.global.apiPayload.code.BaseErrorCode;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LoggingContext {

    private LoggingContext() {
    }

    public static ContextSnapshot withEvent(String event) {
        return with(event, null, Map.of());
    }

    public static ContextSnapshot with(String event, BaseErrorCode errorCode) {
        return with(event, errorCode, Map.of());
    }

    public static ContextSnapshot with(String event, BaseErrorCode errorCode, Map<String, String> additionalEntries) {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        putIfHasText(LoggingMdcKeys.EVENT, event);
        if (errorCode != null) {
            putIfHasText(LoggingMdcKeys.ERROR_CODE, errorCode.getCode());
        }
        additionalEntries.forEach(LoggingContext::putIfHasText);
        return new ContextSnapshot(previousContext);
    }

    private static void putIfHasText(String key, String value) {
        if (StringUtils.hasText(value)) {
            MDC.put(key, value);
        }
    }

    public static final class ContextSnapshot implements AutoCloseable {
        private final Map<String, String> previousContext;

        private ContextSnapshot(Map<String, String> previousContext) {
            this.previousContext = previousContext != null ? new LinkedHashMap<>(previousContext) : null;
        }

        @Override
        public void close() {
            MDC.clear();
            if (previousContext != null && !previousContext.isEmpty()) {
                MDC.setContextMap(previousContext);
            }
        }
    }
}
