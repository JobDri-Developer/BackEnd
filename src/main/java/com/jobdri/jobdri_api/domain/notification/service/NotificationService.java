package com.jobdri.jobdri_api.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.notification.dto.response.NotificationReadAllResponse;
import com.jobdri.jobdri_api.domain.notification.dto.response.NotificationResponse;
import com.jobdri.jobdri_api.domain.notification.dto.response.NotificationStreamBootstrapResponse;
import com.jobdri.jobdri_api.domain.notification.dto.response.NotificationStreamEventResponse;
import com.jobdri.jobdri_api.domain.notification.dto.response.NotificationUnreadCountResponse;
import com.jobdri.jobdri_api.domain.notification.entity.Notification;
import com.jobdri.jobdri_api.domain.notification.entity.NotificationTargetType;
import com.jobdri.jobdri_api.domain.notification.entity.NotificationType;
import com.jobdri.jobdri_api.domain.notification.repository.NotificationRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final NotificationRepository notificationRepository;
    private final NotificationSseService notificationSseService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(User user) {
        User validatedUser = userService.validateUser(user);
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(validatedUser.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse getUnreadCount(User user) {
        User validatedUser = userService.validateUser(user);
        return new NotificationUnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(validatedUser.getId()));
    }

    @Transactional(readOnly = true)
    public SseEmitter subscribe(User user) {
        User validatedUser = userService.validateUser(user);
        return notificationSseService.subscribe(
                validatedUser.getId(),
                () -> new NotificationStreamBootstrapResponse(
                        notificationRepository.countByUserIdAndReadAtIsNull(validatedUser.getId()),
                        notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(validatedUser.getId()).stream()
                                .map(this::toResponse)
                                .toList()
                )
        );
    }

    @Transactional
    public NotificationResponse markRead(User user, Long notificationId) {
        User validatedUser = userService.validateUser(user);
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, validatedUser.getId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "해당 알림을 찾을 수 없습니다. notificationId=" + notificationId
                ));
        boolean wasUnread = !notification.isRead();
        notification.markRead();

        NotificationResponse response = toResponse(notification);
        if (wasUnread) {
            publishAfterCommit(
                    validatedUser.getId(),
                    () -> new NotificationStreamEventResponse(
                            "READ",
                            response,
                            List.of(notification.getId()),
                            notificationRepository.countByUserIdAndReadAtIsNull(validatedUser.getId())
                    )
            );
        }
        return response;
    }

    @Transactional
    public NotificationReadAllResponse markAllRead(User user) {
        User validatedUser = userService.validateUser(user);
        int updatedCount = notificationRepository.markAllAsRead(validatedUser.getId(), LocalDateTime.now());
        if (updatedCount > 0) {
            publishAfterCommit(
                    validatedUser.getId(),
                    () -> new NotificationStreamEventResponse(
                            "READ_ALL",
                            null,
                            null,
                            notificationRepository.countByUserIdAndReadAtIsNull(validatedUser.getId())
                    )
            );
        }
        return new NotificationReadAllResponse(updatedCount, notificationRepository.countByUserIdAndReadAtIsNull(validatedUser.getId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationResponse createNotification(
            Long userId,
            NotificationType notificationType,
            String title,
            String body,
            NotificationTargetType targetType,
            String targetId,
            Map<String, Object> payload
    ) {
        User user = userService.getUser(userId);
        Notification notification = notificationRepository.save(
                Notification.create(
                        user,
                        notificationType,
                        title,
                        body,
                        targetType,
                        targetId,
                        serializePayload(payload)
                )
        );

        NotificationResponse response = toResponse(notification);
        publishAfterCommit(
                userId,
                () -> new NotificationStreamEventResponse(
                        "CREATED",
                        response,
                        List.of(notification.getId()),
                        notificationRepository.countByUserIdAndReadAtIsNull(userId)
                )
        );
        return response;
    }

    private void publishAfterCommit(Long userId, Supplier<NotificationStreamEventResponse> eventSupplier) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notificationSseService.publish(userId, eventSupplier.get());
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationSseService.publish(userId, eventSupplier.get());
            }
        });
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getNotificationType())
                .title(notification.getTitle())
                .body(notification.getBody())
                .isRead(notification.isRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .targetType(notification.getTargetType())
                .targetId(notification.getTargetId())
                .payload(readPayload(notification.getPayloadJson()))
                .build();
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return payload == null || payload.isEmpty() ? null : objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR, "알림 payload 직렬화에 실패했습니다.");
        }
    }

    private Map<String, Object> readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, PAYLOAD_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
