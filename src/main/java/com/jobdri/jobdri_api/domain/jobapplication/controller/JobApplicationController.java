package com.jobdri.jobdri_api.domain.jobapplication.controller;

import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationCreateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationFromJobPostingRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationResponse;
import com.jobdri.jobdri_api.domain.jobapplication.service.JobApplicationService;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-applications")
@Tag(name = "JobApplication", description = "실제 지원관리 카드 API")
public class JobApplicationController {
    private final JobApplicationService jobApplicationService;
    private final UserService userService;

    @Operation(summary = "지원 카드 수동 등록", description = "분석을 시작하지 않고 독립적인 지원관리 카드를 생성합니다.")
    @PostMapping
    public ApiResponse<JobApplicationResponse> create(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody JobApplicationCreateRequest request
    ) {
        var user = validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess("지원 카드 등록에 성공했습니다.", jobApplicationService.create(user, request));
    }

    @Operation(summary = "저장 공고에서 지원 카드 등록", description = "내 저장 공고의 현재 값을 복사해 이후 독립적으로 관리되는 지원 카드를 생성합니다.")
    @PostMapping("/from-job-posting")
    public ApiResponse<JobApplicationResponse> createFromJobPosting(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody JobApplicationFromJobPostingRequest request
    ) {
        var user = validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "저장 공고에서 지원 카드 등록에 성공했습니다.",
                jobApplicationService.createFromJobPosting(user, request)
        );
    }

    @Operation(summary = "지원 카드 단건 조회", description = "현재 로그인한 사용자의 지원 카드 스냅샷을 조회합니다.")
    @GetMapping("/{jobApplicationId}")
    public ApiResponse<JobApplicationResponse> get(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long jobApplicationId
    ) {
        var user = validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess("지원 카드 조회에 성공했습니다.", jobApplicationService.get(user, jobApplicationId));
    }

    private com.jobdri.jobdri_api.domain.user.entity.User validateAuthenticatedUser(UserDetailsImpl userDetails) {
        return userService.validateUser(userDetails == null ? null : userDetails.getUser());
    }
}
