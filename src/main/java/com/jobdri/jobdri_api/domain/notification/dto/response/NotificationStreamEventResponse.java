package com.jobdri.jobdri_api.domain.notification.dto.response;

import java.util.List;

public record NotificationStreamEventResponse(
        String action,
        NotificationResponse notification,
        List<Long> notificationIds,
        long unreadCount
) {
}
