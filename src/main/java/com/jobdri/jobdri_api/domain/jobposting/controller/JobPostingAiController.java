package com.jobdri.jobdri_api.domain.jobposting.controller;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingExtractRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestMultipartRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingExtractMultipartRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAiService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingIngestService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-postings")
@Tag(name = "JobPosting AI", description = "채용 공고 추출 AI API")
public class JobPostingAiController {

    private final JobPostingAiService jobPostingAiService;
    private final JobPostingIngestService jobPostingIngestService;

    @Operation(
            summary = "채용 공고 정보 추출",
            description = "채용 공고 원문 텍스트를 기반으로 회사명, 직무명, 주요 업무, 자격 요건, 우대 사항을 AI로 추출합니다."
    )
    @PostMapping(value = "/extract", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<JobPostingExtractResponse> extractJobPostingFromText(
            @Valid @RequestBody JobPostingExtractRequest request
    ) {
        return ApiResponse.onSuccess(
                "채용 공고 추출에 성공했습니다.",
                jobPostingAiService.extractJobPosting(request.rawText())
        );
    }

    @Operation(
            summary = "채용 공고 정보 추출(이미지 또는 텍스트)",
            description = "프론트에서 캡처한 채용 공고 이미지 파일과 선택적 텍스트, 원본 URL을 함께 보내면 AI가 구조화된 채용 공고 정보를 추출합니다."
    )
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<JobPostingExtractResponse> extractJobPostingFromMultipart(
            @ModelAttribute JobPostingExtractMultipartRequest request
    ) {
        return ApiResponse.onSuccess(
                "채용 공고 추출에 성공했습니다.",
                jobPostingAiService.extractJobPosting(request)
        );
    }

    @Operation(
            summary = "채용 공고 추출부터 분류, 생성, 저장까지 일괄 처리",
            description = "이미지 또는 텍스트 공고를 추출하고, trigram 후보 검색과 AI 재분류를 거쳐 최종 소분류를 선택한 뒤 공고를 생성하고 저장합니다."
    )
    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<JobPostingIngestResponse> ingestJobPosting(
            @ModelAttribute JobPostingIngestMultipartRequest request
    ) {
        return ApiResponse.onSuccess(
                "채용 공고 추출 및 저장에 성공했습니다.",
                jobPostingIngestService.ingestAndCreate(request)
        );
    }
}
