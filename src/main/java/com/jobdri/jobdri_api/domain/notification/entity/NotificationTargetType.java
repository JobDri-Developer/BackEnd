package com.jobdri.jobdri_api.domain.notification.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 이동 대상 종류")
public enum NotificationTargetType {
    @Schema(description = "채용 공고 비동기 작업 상태")
    JOB_POSTING_TASK,

    @Schema(description = "저장된 채용 공고")
    JOB_POSTING_RESULT,

    @Schema(description = "자소서 분석 비동기 작업 상태")
    ANALYSIS_TASK,

    @Schema(description = "자소서 분석 결과")
    ANALYSIS_RESULT,

    @Schema(description = "이동 대상 없음")
    NONE
}
