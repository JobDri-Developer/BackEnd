package com.jobdri.jobdri_api.domain.notification.service;

import com.jobdri.jobdri_api.domain.notification.dto.response.NotificationStreamBootstrapResponse;
import com.jobdri.jobdri_api.domain.notification.dto.response.NotificationStreamEventResponse;
import com.jobdri.jobdri_api.global.sse.SseSubscriptionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class NotificationSseService {

    private static final String BOOTSTRAP_EVENT_NAME = "notification-bootstrap";
    private static final String NOTIFICATION_EVENT_NAME = "notification";

    private final SseSubscriptionRegistry sseSubscriptionRegistry;

    public SseEmitter subscribe(Long userId, Supplier<NotificationStreamBootstrapResponse> bootstrapSupplier) {
        return sseSubscriptionRegistry.subscribe(
                channelKey(userId),
                BOOTSTRAP_EVENT_NAME,
                bootstrapSupplier,
                ignored -> false
        );
    }

    public void publish(Long userId, NotificationStreamEventResponse eventResponse) {
        sseSubscriptionRegistry.publish(channelKey(userId), NOTIFICATION_EVENT_NAME, eventResponse, false);
    }

    private String channelKey(Long userId) {
        return "notification:" + userId;
    }
}
