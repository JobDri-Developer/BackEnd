package com.jobdri.jobdri_api.domain.payment.service;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;

final class TossHttpClientSupport {

    private static final int LOG_MESSAGE_MAX_LENGTH = 500;

    private TossHttpClientSupport() {
    }

    static String truncate(String value) {
        if (value == null || value.length() <= LOG_MESSAGE_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, LOG_MESSAGE_MAX_LENGTH) + "...";
    }

    static boolean isTimeoutException(Throwable throwable) {
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
