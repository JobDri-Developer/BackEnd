package com.jobdri.jobdri_api.domain.notification.entity;

import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    private NotificationTargetType targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public static Notification create(
            User user,
            NotificationType notificationType,
            String title,
            String body,
            NotificationTargetType targetType,
            String targetId,
            String payloadJson
    ) {
        Notification notification = new Notification();
        notification.user = user;
        notification.notificationType = notificationType;
        notification.title = title;
        notification.body = body;
        notification.targetType = targetType;
        notification.targetId = targetId;
        notification.payloadJson = payloadJson;
        return notification;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }
}
