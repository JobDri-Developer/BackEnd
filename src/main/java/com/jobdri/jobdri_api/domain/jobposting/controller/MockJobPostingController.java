package com.jobdri.jobdri_api.domain.jobposting.controller;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingMockGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockQuestionResponse;
import com.jobdri.jobdri_api.domain.jobposting.service.MockJobPostingGenerationService;
import com.jobdri.jobdri_api.domain.jobposting.service.MockQuestionCacheService;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-postings/mock")
@Tag(name = "Mock JobPosting", description = "모의 채용 공고 생성 및 추천 질문 API")
public class MockJobPostingController {

    private final MockJobPostingGenerationService mockJobPostingGenerationService;
    private final MockQuestionCacheService mockQuestionCacheService;
    private final UserService userService;

    @Operation(
            summary = "모의 공고 생성",
            description = "선택한 직무 중분류/소분류를 기반으로 기존 공고를 참고하여 가상의 모의 공고를 생성합니다."
    )
    @PostMapping("/generate")
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
    @GetMapping("/questions")
    public ApiResponse<JobPostingMockQuestionResponse> getMockRecommendedQuestions(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam Long companyId,
            @RequestParam Long middleClassificationId,
            @RequestParam Long detailClassificationId
    ) {
        validateAuthenticatedUser(userDetails);
        JobPostingMockGenerateRequest request = new JobPostingMockGenerateRequest(
                companyId,
                middleClassificationId,
                detailClassificationId
        );
        return ApiResponse.onSuccess(
                "모의 공고 추천 질문 조회에 성공했습니다.",
                new JobPostingMockQuestionResponse(mockQuestionCacheService.getRecommendedQuestions(request))
        );
    }

    @Operation(
            summary = "모의 공고 추천 질문 조회",
            description = "선택한 회사/직무 기준으로 모의 공고 추천 질문을 조회합니다. 하위 호환을 위해 POST 요청도 지원합니다."
    )
    @PostMapping("/questions")
    public ApiResponse<JobPostingMockQuestionResponse> getMockRecommendedQuestionsByPost(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody JobPostingMockGenerateRequest request
    ) {
        validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "모의 공고 추천 질문 조회에 성공했습니다.",
                new JobPostingMockQuestionResponse(mockQuestionCacheService.getRecommendedQuestions(request))
        );
    }

    private com.jobdri.jobdri_api.domain.user.entity.User validateAuthenticatedUser(UserDetailsImpl userDetails) {
        return userService.validateUser(userDetails == null ? null : userDetails.getUser());
    }
}
