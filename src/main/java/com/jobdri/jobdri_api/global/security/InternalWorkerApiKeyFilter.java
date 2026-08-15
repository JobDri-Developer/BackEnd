package com.jobdri.jobdri_api.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class InternalWorkerApiKeyFilter extends OncePerRequestFilter {

    private static final String INTERNAL_WORKER_PATH_PREFIX = "/api/internal/worker/";
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final InternalApiKeyValidator internalApiKeyValidator;
    private final ObjectMapper objectMapper;

    public InternalWorkerApiKeyFilter(
            InternalApiKeyValidator internalApiKeyValidator,
            ObjectMapper objectMapper
    ) {
        this.internalApiKeyValidator = internalApiKeyValidator;
        this.objectMapper = objectMapper;
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
            writeForbiddenResponse(response, exception);
        }
    }

    private void writeForbiddenResponse(HttpServletResponse response, GeneralException exception) throws IOException {
        response.setStatus(GeneralErrorCode.FORBIDDEN.getHttpStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.onFailure(
                        GeneralErrorCode.FORBIDDEN,
                        exception.getError() != null ? exception.getError() : exception.getMessage()
                )
        );
    }
}
