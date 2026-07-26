package com.jobdri.jobdri_api.domain.jobposting.controller;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingUpdateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingService;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-postings")
@Tag(name = "JobPosting", description = "채용 공고 저장/조회/수정/삭제 API")
public class JobPostingController {

    private final JobPostingService jobPostingService;
    private final UserService userService;

    @Operation(summary = "채용 공고 저장", description = "생성되었거나 직접 작성한 채용 공고를 DB에 저장합니다.")
    @PostMapping
    public ApiResponse<JobPostingResponse> createJobPosting(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody JobPostingCreateRequest request
    ) {
        var user = validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "채용 공고 저장에 성공했습니다.",
                jobPostingService.createJobPosting(user, request)
        );
    }

    @Operation(summary = "채용 공고 수정", description = "기존 채용 공고를 수정합니다. 회사명이 없으면 회사를 새로 생성합니다.")
    @PatchMapping("/{jobPostingId}")
    public ApiResponse<JobPostingResponse> updateJobPosting(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long jobPostingId,
            @Valid @RequestBody JobPostingUpdateRequest request
    ) {
        var user = validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "채용 공고 수정에 성공했습니다.",
                jobPostingService.updateJobPosting(user, jobPostingId, request)
        );
    }

    @Operation(summary = "내가 생성한 채용 공고 단건 조회", description = "현재 로그인한 사용자가 생성한 채용 공고를 ID로 단건 조회합니다.")
    @GetMapping("/me/{jobPostingId}")
    public ApiResponse<JobPostingResponse> getMyJobPosting(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long jobPostingId
    ) {
        var user = validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "내 채용 공고 조회에 성공했습니다.",
                jobPostingService.getJobPosting(user, jobPostingId)
        );
    }

    @Operation(summary = "내가 생성한 채용 공고 목록 조회", description = "현재 로그인한 사용자가 생성한 채용 공고 목록을 페이지 단위로 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<Page<JobPostingResponse>> getMyJobPostings(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size
    ) {
        var user = validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "내 채용 공고 목록 조회에 성공했습니다.",
                jobPostingService.getAllJobPostings(user, page, size)
        );
    }

    @Operation(
            summary = "채용 공고 및 모의 서류 결과 전체 삭제",
            description = "채용 공고와 연결된 모의 서류 지원, 문항, 분석 결과를 함께 삭제합니다."
    )
    @DeleteMapping("/{jobPostingId}")
    public ApiResponse<Void> deleteJobPosting(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long jobPostingId
    ) {
        var user = validateAuthenticatedUser(userDetails);
        jobPostingService.deleteJobPosting(user, jobPostingId);
        return ApiResponse.onSuccess("채용 공고와 모의 서류 결과가 삭제되었습니다.", null);
    }

    private com.jobdri.jobdri_api.domain.user.entity.User validateAuthenticatedUser(UserDetailsImpl userDetails) {
        return userService.validateUser(userDetails == null ? null : userDetails.getUser());
    }
}
