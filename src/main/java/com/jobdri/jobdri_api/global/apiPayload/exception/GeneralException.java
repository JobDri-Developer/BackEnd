package com.jobdri.jobdri_api.global.apiPayload.exception;

import com.jobdri.jobdri_api.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    private final BaseErrorCode code;
    private final Object error;

    public GeneralException(BaseErrorCode code) {
        this.code = code;
        this.error = null;
    }

    public GeneralException(BaseErrorCode code, String message) {
        super(message);
        this.code = code;
        this.error = null;
    }

    public GeneralException(BaseErrorCode code, String message, Object error) {
        super(message);
        this.code = code;
        this.error = error;
    }

    public GeneralException(BaseErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.error = null;
    }
}
