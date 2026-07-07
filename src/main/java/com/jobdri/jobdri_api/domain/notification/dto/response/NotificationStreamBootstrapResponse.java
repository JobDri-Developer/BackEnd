package com.jobdri.jobdri_api.domain.notification.dto.response;

import java.util.List;

public record NotificationStreamBootstrapResponse(
        long unreadCount,
        List<NotificationResponse> notifications
) {
}
