package com.jobdri.jobdri_api.domain.jobposting.controller;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingExtractRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncSubmitResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAiService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAsyncFacadeService;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-postings")
@Tag(name = "JobPosting AI", description = "채용 공고 추출 AI API")
public class JobPostingAiController {

    private final JobPostingAiService jobPostingAiService;
    private final JobPostingAsyncFacadeService jobPostingAsyncFacadeService;
    private final UserService userService;

    @Operation(
            summary = "채용 공고 정보 추출",
            description = "채용 공고 원문 텍스트 또는 업로드된 이미지 object key를 기반으로 회사명, 직무명, 주요 업무, 자격 요건, 우대 사항을 AI로 추출합니다."
    )
    @PostMapping(value = "/extract", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<JobPostingExtractResponse> extractJobPosting(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody JobPostingExtractRequest request
    ) {
        validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "채용 공고 추출에 성공했습니다.",
                jobPostingAiService.extractJobPosting(request)
        );
    }

    @Operation(
            summary = "채용 공고 비동기 일괄 처리 접수",
            description = "이미지 또는 텍스트 공고를 비동기로 추출, 분류, 생성, 저장합니다. 응답으로 받은 taskId로 상태를 조회할 수 있습니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "비동기 작업이 정상 접수된 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON2000",
                                      "message": "채용 공고 비동기 작업 접수에 성공했습니다.",
                                      "result": {
                                        "taskId": "f7f4eac0-b241-4d40-bf39-5b10c8a53943",
                                        "status": "PENDING",
                                        "message": "채용 공고 비동기 작업이 접수되었습니다."
                                      },
                                      "error": null
                                    }
                                    """)
                    )
            )
    })
    @PostMapping(value = "/ingest", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<JobPostingAsyncSubmitResponse> ingestJobPosting(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody JobPostingIngestRequest request
    ) {
        var user = validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "채용 공고 비동기 작업 접수에 성공했습니다.",
                jobPostingAsyncFacadeService.submit(user, request)
        );
    }

    @Operation(
            summary = "채용 공고 비동기 작업 상태 조회",
            description = "taskId로 비동기 작업 상태와 결과를 조회합니다."
    )
    @GetMapping("/ingest/async/{taskId}")
    public ApiResponse<JobPostingAsyncStatusResponse> getIngestJobPostingAsyncStatus(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable String taskId
    ) {
        validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "채용 공고 비동기 작업 상태 조회에 성공했습니다.",
                jobPostingAsyncFacadeService.getTask(taskId)
        );
    }

    private com.jobdri.jobdri_api.domain.user.entity.User validateAuthenticatedUser(UserDetailsImpl userDetails) {
        return userService.validateUser(userDetails == null ? null : userDetails.getUser());
    }
}
