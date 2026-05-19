package com.jobdri.jobdri_api.domain.jobposting.controller;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingMockGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingUpdateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockQuestionResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAiService;
import com.jobdri.jobdri_api.domain.jobposting.service.MockQuestionCacheService;
import com.jobdri.jobdri_api.domain.jobposting.service.MockJobPostingGenerationService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-postings")
@Tag(name = "JobPosting", description = "채용 공고 생성/저장/조회 API")
public class JobPostingController {

    private final JobPostingAiService jobPostingAiService;
    private final MockJobPostingGenerationService mockJobPostingGenerationService;
    private final MockQuestionCacheService mockQuestionCacheService;
    private final JobPostingService jobPostingService;
    private final UserService userService;

    @Operation(summary = "채용 공고 초안 생성", description = "회사 정보와 직무 정보를 바탕으로 AI가 공고 본문 초안을 생성합니다.")
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

    @Operation(
            summary = "모의 공고 생성",
            description = "선택한 직무 중분류/소분류를 기반으로 기존 공고를 참고하여 가상의 모의 공고를 생성합니다."
    )
    @PostMapping("/mock/generate")
    public ApiResponse<JobPostingMockGenerateResponse> generateMockJobPosting(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody JobPostingMockGenerateRequest request
    ) {
        validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "모의 공고 생성에 성공했습니다.",
                mockJobPostingGenerationService.generate(request)
        );
    }

    @Operation(
            summary = "모의 공고 추천 질문 조회",
            description = "선택한 회사/직무 기준으로 모의 공고 추천 질문을 조회합니다. 질문은 직무 기준 캐시를 재사용합니다."
    )
    @PostMapping("/mock/questions")
    public ApiResponse<JobPostingMockQuestionResponse> getMockRecommendedQuestions(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody JobPostingMockGenerateRequest request
    ) {
        validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "모의 공고 추천 질문 조회에 성공했습니다.",
                new JobPostingMockQuestionResponse(mockQuestionCacheService.getRecommendedQuestions(request))
        );
    }

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
    @PutMapping("/{jobPostingId}")
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

    @Operation(summary = "채용 공고 단건 조회", description = "채용 공고 ID로 단건 조회합니다.")
    @GetMapping("/{jobPostingId}")
    public ApiResponse<JobPostingResponse> getJobPosting(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long jobPostingId
    ) {
        var user = validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "채용 공고 조회에 성공했습니다.",
                jobPostingService.getJobPosting(user, jobPostingId)
        );
    }

    @Operation(summary = "채용 공고 목록 조회", description = "전체 공고 또는 회사별 공고 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<JobPostingResponse>> getJobPostings(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) Long companyId
    ) {
        var user = validateAuthenticatedUser(userDetails);
        List<JobPostingResponse> result = companyId == null
                ? jobPostingService.getAllJobPostings(user)
                : jobPostingService.getJobPostingsByCompany(user, companyId);

        return ApiResponse.onSuccess("채용 공고 목록 조회에 성공했습니다.", result);
    }

    private com.jobdri.jobdri_api.domain.user.entity.User validateAuthenticatedUser(UserDetailsImpl userDetails) {
        return userService.validateUser(userDetails == null ? null : userDetails.getUser());
    }
}
