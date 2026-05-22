package com.jobdri.jobdri_api.domain.audit.aop;

import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.audit.service.AuditLogService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final String RESULT_VARIABLE = "result";

    private final AuditLogService auditLogService;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(auditLogEvent)")
    public Object recordAuditLog(ProceedingJoinPoint joinPoint, AuditLogEvent auditLogEvent) throws Throwable {
        Map<String, Object> beforeValue = extractParameters(joinPoint);

        Object result = joinPoint.proceed();

        try {
            auditLogService.record(
                    extractUser(joinPoint),
                    auditLogEvent.action(),
                    auditLogEvent.targetType(),
                    evaluateTargetId(joinPoint, auditLogEvent.targetId(), result),
                    beforeValue,
                    result
            );
        } catch (RuntimeException e) {
            log.warn("Audit log recording failed. action={}, method={}",
                    auditLogEvent.action(),
                    joinPoint.getSignature().toShortString(),
                    e
            );
            throw e;
        }

        return result;
    }

    private User extractUser(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof User user) {
                return user;
            }
        }
        return null;
    }

    private Long evaluateTargetId(ProceedingJoinPoint joinPoint, String targetIdExpression, Object result) {
        if (targetIdExpression == null || targetIdExpression.isBlank()) {
            return null;
        }

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                null,
                method,
                joinPoint.getArgs(),
                parameterNameDiscoverer
        );
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < args.length; i++) {
            context.setVariable("arg" + i, args[i]);
            context.setVariable("p" + i, args[i]);
        }
        context.setVariable(RESULT_VARIABLE, result);

        Object value = expressionParser.parseExpression(targetIdExpression).getValue(context);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        return null;
    }

    private Map<String, Object> extractParameters(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        Map<String, Object> parameters = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof User) {
                continue;
            }
            String parameterName = parameterNames != null && i < parameterNames.length
                    ? parameterNames[i]
                    : "arg" + i;
            parameters.put(parameterName, args[i]);
        }
        return parameters;
    }
}
