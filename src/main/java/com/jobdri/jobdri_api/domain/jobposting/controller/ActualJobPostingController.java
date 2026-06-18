package com.jobdri.jobdri_api.domain.jobposting.controller;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAiService;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-postings")
@Tag(name = "Actual JobPosting", description = "실제 채용 공고 초안 생성 API")
public class ActualJobPostingController {

    private final JobPostingAiService jobPostingAiService;
    private final UserService userService;

    @Operation(summary = "실제 채용 공고 초안 생성", description = "회사 정보와 직무 정보를 바탕으로 AI가 실제 채용 공고 본문 초안을 생성합니다.")
    @PostMapping("/generate")
    public ApiResponse<JobPostingGenerateResponse> generateJobPosting(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody JobPostingGenerateRequest request
    ) {
        validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "채용 공고 초안 생성에 성공했습니다.",
                jobPostingAiService.generateJobPosting(request)
        );
    }

    private com.jobdri.jobdri_api.domain.user.entity.User validateAuthenticatedUser(UserDetailsImpl userDetails) {
        return userService.validateUser(userDetails == null ? null : userDetails.getUser());
    }
}
