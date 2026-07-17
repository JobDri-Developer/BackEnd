package com.jobdri.jobdri_api.domain.notification.dto.response;

import com.jobdri.jobdri_api.domain.notification.entity.NotificationTargetType;
import com.jobdri.jobdri_api.domain.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Builder
@Schema(description = "알림 응답")
public record NotificationResponse(
        @Schema(description = "알림 ID", example = "1")
        Long id,

        @Schema(description = "알림 종류", example = "JOB_POSTING_ASYNC_SUCCEEDED")
        NotificationType type,

        @Schema(description = "알림 제목", example = "채용 공고 작업이 완료되었습니다.")
        String title,

        @Schema(description = "알림 본문", example = "채용 공고 분석과 저장이 완료되었습니다.")
        String body,

        @Schema(description = "읽음 여부", example = "false")
        boolean isRead,

        @Schema(description = "읽음 처리 시각. 읽지 않은 알림이면 null입니다.", example = "2026-07-16T14:17:35.672")
        LocalDateTime readAt,

        @Schema(description = "알림 생성 시각", example = "2026-07-16T14:17:35.672")
        LocalDateTime createdAt,

        @Schema(description = "알림 클릭 시 이동 대상 종류", example = "JOB_POSTING_RESULT")
        NotificationTargetType targetType,

        @Schema(description = "알림 클릭 시 이동 대상 ID", example = "1")
        String targetId,

        @Schema(description = "알림 타입별 부가 데이터", implementation = NotificationPayloadResponse.class)
        Map<String, Object> payload
) {
}
