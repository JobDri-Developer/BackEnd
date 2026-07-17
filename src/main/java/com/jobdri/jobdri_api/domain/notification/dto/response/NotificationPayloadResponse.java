package com.jobdri.jobdri_api.domain.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "알림 타입별 부가 데이터")
public record NotificationPayloadResponse(
        @Schema(description = "비동기 작업 ID", example = "job-posting-task-123")
        String taskId,

        @Schema(description = "저장된 채용 공고 ID. 채용 공고 작업 성공 알림에서 제공됩니다.", example = "1")
        Long jobPostingId,

        @Schema(description = "모의 지원 ID. 자소서 분석 알림에서 제공됩니다.", example = "10")
        Long mockApplyId,

        @Schema(description = "채용 공고 저장 여부. 채용 공고 작업 성공 알림에서 제공됩니다.", example = "true")
        Boolean savedToDatabase,

        @Schema(description = "작업 상태. 실패 알림 또는 분석 알림에서 제공됩니다.", example = "SUCCEEDED")
        String status,

        @Schema(description = "실패 사유. 실패 알림에서 제공됩니다.", example = "WORKER_ERROR")
        String failureReason
) {
}
