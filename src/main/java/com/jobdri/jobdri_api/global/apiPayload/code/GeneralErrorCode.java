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

    // 채용 공고 에러
    JOB_POSTING_NOT_FOUND(HttpStatus.NOT_FOUND, "JOB_POSTING_4041", "채용 공고를 찾을 수 없습니다."),

    // 유저 에러
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_4041", "유저를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
