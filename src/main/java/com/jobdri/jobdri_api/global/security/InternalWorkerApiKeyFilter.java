package com.jobdri.jobdri_api.global.security;

import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

public class InternalWorkerApiKeyFilter extends OncePerRequestFilter {

    private static final String INTERNAL_WORKER_PATH_PREFIX = "/api/internal/worker/";
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final InternalApiKeyValidator internalApiKeyValidator;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public InternalWorkerApiKeyFilter(
            InternalApiKeyValidator internalApiKeyValidator,
            HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.internalApiKeyValidator = internalApiKeyValidator;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_WORKER_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            internalApiKeyValidator.validate(request.getHeader(INTERNAL_API_KEY_HEADER));
            filterChain.doFilter(request, response);
        } catch (GeneralException exception) {
            handlerExceptionResolver.resolveException(request, response, null, exception);
        }
    }
}
