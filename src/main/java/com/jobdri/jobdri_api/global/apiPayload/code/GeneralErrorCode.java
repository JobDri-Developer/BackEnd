package com.jobdri.jobdri_api.global.apiPayload.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GeneralErrorCode implements BaseErrorCode {

    // 인증 에러
    DUPLICATE_LOGINID(HttpStatus.BAD_REQUEST, "AUTH_4001", "중복되는 아이디가 존재합니다."),
    INVALID_AUTH_CODE(HttpStatus.BAD_REQUEST, "AUTH_4002", "이메일 인증번호가 유효하지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "AUTH_4003", "이메일 인증이 필요합니다."),
    SOCIAL_LOGIN_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_4004", "소셜 로그인을 이용해주세요."),
    MISSING_AUTH_INFO(HttpStatus.UNAUTHORIZED, "AUTH_4011", "인증 정보가 누락되었습니다."),
    INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "AUTH_4012", "올바르지 않은 아이디, 혹은 비밀번호입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_4012", "유효하지 않은 토큰입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_4031", "접근 권한이 없습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_4191", "토큰이 만료되었습니다."),

    // 서버 내부 에러
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_5001", "서버 내부 오류입니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SERVER_5031", "서버가 일시적으로 불안정합니다."),
    EXTERNAL_SERVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "SERVER_5041", "외부 서비스 응답 지연"),

    // 요청 파라미터 에러
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "REQ_4001", "필수 파라미터가 누락되었습니다."),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "REQ_4002", "파라미터 형식이 잘못되었습니다."),
    UNSUPPORTED_CONTENT_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "REQ_4151", "지원하지 않는 Content-Type입니다."),

    // 분류 에러
    CLASSIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CLASSIFICATION_4041", "분류를 찾을 수 없습니다."),

    // 회사 에러
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMPANY_4041", "회사를 찾을 수 없습니다."),

    // 채용 공고 에러
    JOB_POSTING_NOT_FOUND(HttpStatus.NOT_FOUND, "JOB_POSTING_4041", "채용 공고를 찾을 수 없습니다."),
    JOB_POSTING_ASYNC_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "JOB_POSTING_4042", "채용 공고 비동기 작업을 찾을 수 없습니다."),
    JOB_POSTING_UPDATE_CONFLICT(HttpStatus.CONFLICT, "JOB_POSTING_4091", "채용 공고가 이미 수정되었습니다."),
    WORKER_TASK_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "WORKER_RESULT_4041", "worker 결과를 찾을 수 없습니다."),

    // 모의 서류 지원 에러
    MOCK_APPLY_NOT_FOUND(HttpStatus.NOT_FOUND, "MOCK_APPLY_4041", "모의 서류 지원을 찾을 수 없습니다."),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTION_4041", "문항을 찾을 수 없습니다."),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "ANALYSIS_4041", "자소서 분석 결과를 찾을 수 없습니다."),
    ANALYSIS_ASYNC_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "ANALYSIS_4042", "자소서 분석 비동기 작업을 찾을 수 없습니다."),

    // 결제/크레딧 에러
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_4041", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT_4001", "결제 금액이 일치하지 않습니다."),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.BAD_REQUEST, "PAYMENT_4002", "이미 처리된 결제입니다."),
    PAYMENT_CONFIRM_FAILED(HttpStatus.BAD_GATEWAY, "PAYMENT_5021", "결제 승인에 실패했습니다."),
    INSUFFICIENT_CREDIT(HttpStatus.PAYMENT_REQUIRED, "CREDIT_4021", "크레딧이 부족합니다."),
    COUPON_INVALID(HttpStatus.BAD_REQUEST, "COUPON_4001", "유효하지 않은 쿠폰입니다."),
    COUPON_ALREADY_REDEEMED(HttpStatus.CONFLICT, "COUPON_4091", "이미 사용한 쿠폰입니다."),

    // 유저 에러
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_4041", "유저를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
