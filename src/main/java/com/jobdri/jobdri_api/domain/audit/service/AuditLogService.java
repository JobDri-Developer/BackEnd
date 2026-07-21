package com.jobdri.jobdri_api.domain.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.audit.entity.AuditLog;
import com.jobdri.jobdri_api.domain.audit.repository.AuditLogRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.logging.LoggingContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.jobdri.jobdri_api.global.logging.LoggingMdcKeys;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("AUDIT");

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(
            User user,
            String action,
            String targetType,
            Long targetId,
            Object beforeValue,
            Object afterValue
    ) {
        HttpServletRequest request = currentRequest();
        AuditLog auditLog = auditLogRepository.save(AuditLog.create(
                user,
                action,
                targetType,
                targetId,
                toJson(beforeValue),
                toJson(afterValue),
                resolveIpAddress(request),
                truncate(resolveUserAgent(request), MAX_USER_AGENT_LENGTH)
        ));
        writeAuditTrail(auditLog);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String resolveIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getHeader("User-Agent");
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void writeAuditTrail(AuditLog auditLog) {
        Map<String, String> auditContext = new LinkedHashMap<>();
        auditContext.put(LoggingMdcKeys.LOG_TYPE, "audit");
        auditContext.put("auditAction", auditLog.getAction());
        auditContext.put("auditTargetType", auditLog.getTargetType());
        if (auditLog.getTargetId() != null) {
            auditContext.put("auditTargetId", String.valueOf(auditLog.getTargetId()));
        }
        try (var ignored = LoggingContext.with("audit.recorded", null, auditContext)) {
            AUDIT_LOGGER.info("Audit log persisted");
        }
    }
}
