package com.jobdri.jobdri_api.domain.jobapplication.controller;

import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationFavoriteRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationFavoriteResponse;
import com.jobdri.jobdri_api.domain.jobapplication.service.JobApplicationFavoriteService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-applications")
@Tag(name = "JobApplication", description = "실제 지원관리 카드 API")
public class JobApplicationFavoriteController {
    private final JobApplicationFavoriteService service;

    @PatchMapping("/{jobApplicationId}/favorite")
    @Operation(summary = "지원 공고 즐겨찾기 설정·해제", description = "favorite에 원하는 값을 지정합니다. 반복 요청에도 상태가 유지되며 상세 revision과 단계·순서는 변경하지 않습니다.")
    public ApiResponse<JobApplicationFavoriteResponse> set(
            @AuthenticationPrincipal UserDetailsImpl principal, @PathVariable Long jobApplicationId,
            @Valid @RequestBody JobApplicationFavoriteRequest request) {
        return ApiResponse.onSuccess("즐겨찾기 설정에 성공했습니다.",
                service.set(principal == null ? null : principal.getUser(), jobApplicationId, request.favorite()));
    }
}
