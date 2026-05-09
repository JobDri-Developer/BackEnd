package com.jobdri.jobdri_api.global.apiPayload.exception;

import com.jobdri.jobdri_api.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    private final BaseErrorCode code;

    public GeneralException(BaseErrorCode code) {
        this.code = code;
    }

    public GeneralException(BaseErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
