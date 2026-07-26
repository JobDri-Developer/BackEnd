package com.jobdri.jobdri_api.domain.analysis.controller;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncCancelResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncSubmitResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisAsyncFacadeService;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisAsyncSseService;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mock-applies/{mockApplyId}/analysis")
@Tag(name = "Analysis", description = "자소서 분석 API")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final AnalysisAsyncFacadeService analysisAsyncFacadeService;
    private final AnalysisAsyncSseService analysisAsyncSseService;

    @Operation(summary = "자소서 분석 비동기 실행", description = "저장된 문항 답변과 공고 정보를 기반으로 자소서 분석 작업을 접수합니다.")
    @PostMapping
    public ApiResponse<AnalysisAsyncSubmitResponse> analyze(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId
    ) {
        return ApiResponse.onSuccess(
                "자소서 분석 비동기 작업이 접수되었습니다.",
                analysisAsyncFacadeService.submit(getAuthenticatedUser(userDetails), mockApplyId)
        );
    }

    @Operation(summary = "자소서 분석 비동기 작업 상태 조회", description = "taskId로 자소서 분석 비동기 작업 상태를 조회합니다.")
    @GetMapping("/async/{taskId}")
    public ApiResponse<AnalysisAsyncStatusResponse> getAnalysisTask(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId,
            @PathVariable String taskId
    ) {
        return ApiResponse.onSuccess(
                "자소서 분석 비동기 작업 상태 조회에 성공했습니다.",
                analysisAsyncFacadeService.getTask(getAuthenticatedUser(userDetails), mockApplyId, taskId)
        );
    }

    @Operation(summary = "자소서 분석 비동기 작업 취소", description = "taskId로 접수된 자소서 분석 비동기 작업을 취소합니다.")
    @PostMapping("/async/{taskId}/cancel")
    public ApiResponse<AnalysisAsyncCancelResponse> cancelAnalysisTask(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId,
            @PathVariable String taskId
    ) {
        return ApiResponse.onSuccess(
                "자소서 분석 비동기 작업 취소에 성공했습니다.",
                analysisAsyncFacadeService.cancel(getAuthenticatedUser(userDetails), mockApplyId, taskId)
        );
    }

    @Operation(summary = "자소서 분석 비동기 작업 상태 SSE 구독", description = "taskId로 자소서 분석 비동기 작업 상태를 SSE 스트림으로 구독합니다.")
    @GetMapping(value = "/async/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnalysisTask(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId,
            @PathVariable String taskId
    ) {
        var user = getAuthenticatedUser(userDetails);
        return analysisAsyncSseService.subscribe(
                taskId,
                () -> analysisAsyncFacadeService.getTask(user, mockApplyId, taskId)
        );
    }

    @Operation(summary = "자소서 분석 결과 조회", description = "저장된 자소서 분석 결과를 조회합니다.")
    @GetMapping
    public ApiResponse<AnalysisResponse> getAnalysis(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId
    ) {
        return ApiResponse.onSuccess(
                "자소서 분석 결과 조회에 성공했습니다.",
                analysisService.getAnalysis(getAuthenticatedUser(userDetails), mockApplyId)
        );
    }

    private com.jobdri.jobdri_api.domain.user.entity.User getAuthenticatedUser(UserDetailsImpl userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            throw new GeneralException(GeneralErrorCode.MISSING_AUTH_INFO, "인증 정보가 누락되었습니다.");
        }
        return userDetails.getUser();
    }
}
