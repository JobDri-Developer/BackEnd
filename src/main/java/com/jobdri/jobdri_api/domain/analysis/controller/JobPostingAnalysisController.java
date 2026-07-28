package com.jobdri.jobdri_api.domain.analysis.controller;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-postings/{jobPostingId}/analysis")
@Tag(name = "Analysis", description = "자소서 분석 API")
public class JobPostingAnalysisController {

    private final AnalysisService analysisService;

    @Operation(summary = "지원 회차별 자소서 분석 결과 조회", description = "공고 기준으로 지정한 지원 회차의 자소서 분석 결과를 조회합니다.")
    @GetMapping
    public ApiResponse<AnalysisResponse> getAnalysisBySequence(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long jobPostingId,
            @RequestParam Integer sequence
    ) {
        return ApiResponse.onSuccess(
                "자소서 분석 결과 조회에 성공했습니다.",
                analysisService.getAnalysisByJobPostingSequence(getAuthenticatedUser(userDetails), jobPostingId, sequence)
        );
    }

    private com.jobdri.jobdri_api.domain.user.entity.User getAuthenticatedUser(UserDetailsImpl userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            throw new GeneralException(GeneralErrorCode.MISSING_AUTH_INFO, "인증 정보가 누락되었습니다.");
        }
        return userDetails.getUser();
    }
}
