package com.jobdri.jobdri_api.domain.jobposting.controller;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingExtensionIngestRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtensionIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingExtensionIngestService;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-postings/extension")
@Tag(name = "JobPosting Extension", description = "크롬 익스텐션 채용 공고 연계 API")
public class JobPostingExtensionController {

    private final JobPostingExtensionIngestService jobPostingExtensionIngestService;
    private final UserService userService;

    @Operation(
            summary = "크롬 익스텐션 공고 수집 및 모의 서류 생성",
            description = "크롬 익스텐션이 크롤링한 채용 공고 원문을 기반으로 공고를 저장하고 모의 서류 지원을 생성합니다."
    )
    @PostMapping(value = "/ingest", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<JobPostingExtensionIngestResponse> ingestFromExtension(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody JobPostingExtensionIngestRequest request
    ) {
        var user = userService.validateUser(userDetails == null ? null : userDetails.getUser());
        return ApiResponse.onSuccess(
                "크롬 익스텐션 공고 수집에 성공했습니다.",
                jobPostingExtensionIngestService.ingest(user, request)
        );
    }
}
