package com.jobdri.jobdri_api.domain.analysis.controller;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mock-applies/{mockApplyId}/analysis")
@Tag(name = "Analysis", description = "자소서 분석 API")
public class AnalysisController {

    private final AnalysisService analysisService;

    @Operation(summary = "자소서 분석 실행", description = "저장된 문항 답변과 공고 정보를 기반으로 자소서를 분석하고 결과를 저장합니다.")
    @PostMapping
    public ApiResponse<AnalysisResponse> analyze(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId
    ) {
        return ApiResponse.onSuccess(
                "자소서 분석이 완료되었습니다.",
                analysisService.analyze(getAuthenticatedUser(userDetails), mockApplyId)
        );
    }

    @Operation(summary = "자소서 분석 결과 조회", description = "저장된 자소서 분석 결과를 조회합니다.")
    @GetMapping
    public ApiResponse<AnalysisResponse> getAnalysis(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId,
            @RequestParam(required = false) Integer sequence
    ) {
        return ApiResponse.onSuccess(
                "자소서 분석 결과 조회에 성공했습니다.",
                analysisService.getAnalysis(getAuthenticatedUser(userDetails), mockApplyId, sequence)
        );
    }

    private com.jobdri.jobdri_api.domain.user.entity.User getAuthenticatedUser(UserDetailsImpl userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            throw new GeneralException(GeneralErrorCode.MISSING_AUTH_INFO, "인증 정보가 누락되었습니다.");
        }
        return userDetails.getUser();
    }
}
