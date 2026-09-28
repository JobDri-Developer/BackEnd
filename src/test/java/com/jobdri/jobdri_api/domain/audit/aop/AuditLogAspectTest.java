package com.jobdri.jobdri_api.domain.audit.aop;

import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.audit.service.AuditLogService;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationIngestResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogAspectTest {
    @Mock AuditLogService auditLogService;
    @Mock ProceedingJoinPoint joinPoint;
    @Mock MethodSignature signature;

    @Test
    void skipsAuditWhenConditionIsFalse() throws Throwable {
        JobApplicationIngestResponse response = response(false);
        AuditLogEvent event = prepareJoinPoint(response);

        Object result = new AuditLogAspect(auditLogService).recordAuditLog(joinPoint, event);

        assertThat(result).isSameAs(response);
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void recordsAuditWhenConditionIsTrue() throws Throwable {
        JobApplicationIngestResponse response = response(true);
        AuditLogEvent event = prepareJoinPoint(response);

        new AuditLogAspect(auditLogService).recordAuditLog(joinPoint, event);

        verify(auditLogService).record(
                eq(null), eq("TEST_INGEST"), eq("JOB_APPLICATION"), eq(null), any(), eq(response)
        );
    }

    private AuditLogEvent prepareJoinPoint(JobApplicationIngestResponse response) throws Throwable {
        Method method = Fixture.class.getDeclaredMethod("ingest", String.class);
        when(joinPoint.proceed()).thenReturn(response);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[]{"request"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"request"});
        return method.getAnnotation(AuditLogEvent.class);
    }

    private JobApplicationIngestResponse response(boolean saved) {
        return new JobApplicationIngestResponse(
                saved, false, "message", null, List.of(), null, null, null
        );
    }

    private static class Fixture {
        @AuditLogEvent(
                action = "TEST_INGEST",
                targetType = "JOB_APPLICATION",
                condition = "#result.savedToDatabase"
        )
        @SuppressWarnings("unused")
        JobApplicationIngestResponse ingest(String request) {
            return null;
        }
    }
}
