package com.jobdri.jobdri_api.domain.notification.dto.response;

import com.jobdri.jobdri_api.domain.notification.entity.NotificationTargetType;
import com.jobdri.jobdri_api.domain.notification.entity.NotificationType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Builder
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        boolean isRead,
        LocalDateTime readAt,
        LocalDateTime createdAt,
        NotificationTargetType targetType,
        String targetId,
        Map<String, Object> payload
) {
}
