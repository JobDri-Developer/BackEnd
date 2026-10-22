package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationMetricType;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 지원 카드와 마스터 이력서가 같은 규칙으로 정량 정보 행을 저장하도록 맞춘다.
 * 이름이 빈 행과 값이 빈 어학·기타 행은 400 대신 제외하고, 값이 선택인 자격증·수상은 기본값을 채운다.
 */
public final class MetricRowNormalizer {
    public static final String DEFAULT_CERTIFICATE_VALUE = "보유";
    public static final String DEFAULT_AWARD_VALUE = "취득일 미입력";

    private MetricRowNormalizer() {
    }

    public record Row(JobApplicationMetricType type, String name, String value) {
    }

    public static <T> List<Row> normalize(
            List<T> rows,
            Function<T, JobApplicationMetricType> type,
            Function<T, String> name,
            Function<T, String> value
    ) {
        return (rows == null ? List.<T>of() : rows).stream()
                .filter(Objects::nonNull)
                .map(row -> normalize(type.apply(row), name.apply(row), value.apply(row)))
                .filter(Objects::nonNull)
                .toList();
    }

    private static Row normalize(JobApplicationMetricType type, String name, String value) {
        if (!hasText(name)) {
            return null;
        }
        if (type == null) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "정량 스펙 유형은 필수입니다.");
        }
        if (hasText(value)) {
            return new Row(type, name.trim(), value.trim());
        }
        return switch (type) {
            case CERTIFICATE -> new Row(type, name.trim(), DEFAULT_CERTIFICATE_VALUE);
            case AWARD -> new Row(type, name.trim(), DEFAULT_AWARD_VALUE);
            default -> null;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
