package com.jobdri.jobdri_api.domain.notification.controller;

import com.jobdri.jobdri_api.domain.notification.dto.response.NotificationReadAllResponse;
import com.jobdri.jobdri_api.domain.notification.dto.response.NotificationResponse;
import com.jobdri.jobdri_api.domain.notification.dto.response.NotificationUnreadCountResponse;
import com.jobdri.jobdri_api.domain.notification.service.NotificationService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
@Tag(name = "Notification", description = "인앱 알림 API")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 목록 조회", description = "최근 알림 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "알림 목록 조회에 성공했습니다.",
                notificationService.getNotifications(userDetails.getUser())
        );
    }

    @Operation(summary = "미읽음 알림 개수 조회", description = "현재 미읽음 알림 개수를 조회합니다.")
    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "미읽음 알림 개수 조회에 성공했습니다.",
                notificationService.getUnreadCount(userDetails.getUser())
        );
    }

    @Operation(summary = "알림 SSE 구독", description = "실시간 알림 스트림을 구독합니다.")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamNotifications(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noCache().getHeaderValue())
                .header("X-Accel-Buffering", "no")
                .body(notificationService.subscribe(userDetails.getUser()));
    }

    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 처리합니다.")
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> markRead(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long notificationId
    ) {
        return ApiResponse.onSuccess(
                "알림 읽음 처리에 성공했습니다.",
                notificationService.markRead(userDetails.getUser(), notificationId)
        );
    }

    @Operation(summary = "알림 전체 읽음 처리", description = "미읽음 알림을 모두 읽음 처리합니다.")
    @PatchMapping("/read-all")
    public ApiResponse<NotificationReadAllResponse> markAllRead(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "알림 전체 읽음 처리에 성공했습니다.",
                notificationService.markAllRead(userDetails.getUser())
        );
    }
}
