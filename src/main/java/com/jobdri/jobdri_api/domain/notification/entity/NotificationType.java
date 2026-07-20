package com.jobdri.jobdri_api.domain.notification.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 종류")
public enum NotificationType {
    @Schema(description = "채용 공고 비동기 작업 성공")
    JOB_POSTING_ASYNC_SUCCEEDED,

    @Schema(description = "채용 공고 비동기 작업 실패")
    JOB_POSTING_ASYNC_FAILED,

    @Schema(description = "자소서 분석 비동기 작업 성공")
    ANALYSIS_ASYNC_SUCCEEDED,

    @Schema(description = "자소서 분석 비동기 작업 실패")
    ANALYSIS_ASYNC_FAILED,

    @Schema(description = "일반 알림")
    GENERAL
}
