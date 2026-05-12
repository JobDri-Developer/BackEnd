package com.jobdri.jobdri_api.domain.jobposting.controller;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAiService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-postings")
@Tag(name = "JobPosting", description = "채용 공고 생성/저장/조회 API")
public class JobPostingController {

    private final JobPostingAiService jobPostingAiService;
    private final JobPostingService jobPostingService;

    @Operation(summary = "채용 공고 초안 생성", description = "회사 정보와 직무 정보를 바탕으로 AI가 공고 본문 초안을 생성합니다.")
    @PostMapping("/generate")
    public ApiResponse<JobPostingGenerateResponse> generateJobPosting(
            @Valid @RequestBody JobPostingGenerateRequest request
    ) {
        return ApiResponse.onSuccess(
                "채용 공고 초안 생성에 성공했습니다.",
                jobPostingAiService.generateJobPosting(request)
        );
    }

    @Operation(summary = "채용 공고 저장", description = "생성되었거나 직접 작성한 채용 공고를 DB에 저장합니다.")
    @PostMapping
    public ApiResponse<JobPostingResponse> createJobPosting(
            @Valid @RequestBody JobPostingCreateRequest request
    ) {
        return ApiResponse.onSuccess(
                "채용 공고 저장에 성공했습니다.",
                jobPostingService.createJobPosting(request)
        );
    }

    @Operation(summary = "채용 공고 단건 조회", description = "채용 공고 ID로 단건 조회합니다.")
    @GetMapping("/{jobPostingId}")
    public ApiResponse<JobPostingResponse> getJobPosting(@PathVariable Long jobPostingId) {
        return ApiResponse.onSuccess(
                "채용 공고 조회에 성공했습니다.",
                jobPostingService.getJobPosting(jobPostingId)
        );
    }

    @Operation(summary = "채용 공고 목록 조회", description = "전체 공고 또는 회사별 공고 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<JobPostingResponse>> getJobPostings(
            @RequestParam(required = false) Long companyId
    ) {
        List<JobPostingResponse> result = companyId == null
                ? jobPostingService.getAllJobPostings()
                : jobPostingService.getJobPostingsByCompany(companyId);

        return ApiResponse.onSuccess("채용 공고 목록 조회에 성공했습니다.", result);
    }
}
