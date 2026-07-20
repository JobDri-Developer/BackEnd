package com.jobdri.jobdri_api.global.apiPayload.exception.handler;

import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.BaseErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class ExceptionAdvice {
    private static final String MASKED_VALUE = "****";
    private static final String DUPLICATE_KEYWORD = "duplicate";
    private static final String UNIQUE_KEYWORD = "unique";
    private static final String UNIQUE_CONSTRAINT_PREFIX = "uk_";
    private static final Set<String> SENSITIVE_FIELD_KEYWORDS = Set.of(
            "password",
            "token",
            "secret",
            "authorization",
            "credential"
    );

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<Object>> handleCustomException(GeneralException e) {
        log.warn("CustomException: {}", e.getCode().getMessage());
        BaseErrorCode code = e.getCode();
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ApiResponse.onFailure(code, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException e) {
        var errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> String.format("[%s] %s (입력값: %s)",
                        fe.getField(),
                        fe.getDefaultMessage(),
                        formatRejectedValue(fe.getField(), fe.getRejectedValue())))
                .toList();

        log.warn("Validation failed: {}", errors);

        BaseErrorCode code = GeneralErrorCode.INVALID_PARAMETER;
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure(code, errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(ConstraintViolationException e) {
        List<String> errors = e.getConstraintViolations().stream()
                .map(violation -> {
                    String propertyPath = violation.getPropertyPath().toString();
                    return String.format("[%s] %s (입력값: %s)",
                            propertyPath,
                            violation.getMessage(),
                            formatRejectedValue(propertyPath, violation.getInvalidValue()));
                })
                .collect(Collectors.toList());

        log.warn("Constraint violation: {}", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure(GeneralErrorCode.INVALID_PARAMETER, errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleJsonErrors(HttpMessageNotReadableException e) {
        log.warn("JSON Parse Error: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure(GeneralErrorCode.INVALID_PARAMETER, "입력값이 잘못되었습니다. (JSON 형식을 확인해주세요)"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e
    ) {
        String parameterName = e.getName();
        String message = String.format(
                "파라미터 '%s'의 형식이 잘못되었습니다. (입력값: %s)",
                parameterName,
                formatRejectedValue(parameterName, e.getValue())
        );

        log.warn("Method argument type mismatch: {}", message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure(GeneralErrorCode.INVALID_PARAMETER, message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolationException: {}", buildDeepestMessage(e));
        if (isDuplicateConflict(e)) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.onFailure(GeneralErrorCode.INVALID_PARAMETER, "이미 처리되었거나 중복된 요청입니다."));
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.onFailure(
                        GeneralErrorCode.INTERNAL_SERVER_ERROR,
                        "데이터 저장 중 무결성 오류가 발생했습니다."
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        log.warn("Exception: {}", e.getMessage());
        BaseErrorCode code = GeneralErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ApiResponse.onFailure(code, code.getMessage()));
    }

    static String formatRejectedValue(String field, Object rejectedValue) {
        if (isSensitiveField(field)) {
            return MASKED_VALUE;
        }
        return String.valueOf(rejectedValue);
    }

    static boolean isSensitiveField(String field) {
        if (field == null) {
            return false;
        }

        String normalizedField = field.toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELD_KEYWORDS.stream()
                .anyMatch(normalizedField::contains);
    }

    static boolean isDuplicateConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraintViolation
                    && containsDuplicateConstraintHint(constraintViolation.getConstraintName())) {
                return true;
            }
            if (containsDuplicateConstraintHint(cause.getMessage())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static boolean containsDuplicateConstraintHint(String value) {
        if (value == null) {
            return false;
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains(DUPLICATE_KEYWORD)
                || normalized.contains(UNIQUE_KEYWORD)
                || normalized.contains(UNIQUE_CONSTRAINT_PREFIX);
    }

    private static String buildDeepestMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = throwable.getMessage();
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return message;
    }
}
