package com.jobdri.jobdri_api.domain.jobposting.controller;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingExtractRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestMultipartRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingExtractMultipartRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncSubmitResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAiService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAsyncFacadeService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingIngestService;
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
import org.springframework.web.bind.annotation.ModelAttribute;
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
    private final JobPostingIngestService jobPostingIngestService;
    private final JobPostingAsyncFacadeService jobPostingAsyncFacadeService;
    private final UserService userService;

    @Operation(
            summary = "채용 공고 정보 추출",
            description = "채용 공고 원문 텍스트를 기반으로 회사명, 직무명, 주요 업무, 자격 요건, 우대 사항을 AI로 추출합니다."
    )
    @PostMapping(value = "/extract", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<JobPostingExtractResponse> extractJobPostingFromText(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody JobPostingExtractRequest request
    ) {
        validateAuthenticatedUser(userDetails);
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
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @ModelAttribute JobPostingExtractMultipartRequest request
    ) {
        validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "채용 공고 추출에 성공했습니다.",
                jobPostingAiService.extractJobPosting(request)
        );
    }

    @Operation(
            summary = "채용 공고 추출부터 분류, 생성, 저장까지 일괄 처리",
            description = "이미지 또는 텍스트 공고를 추출하고, trigram 후보 검색과 AI 재분류를 거쳐 최종 소분류를 선택한 뒤 공고를 생성하고 저장합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "분류 confidence가 충분하여 저장까지 완료된 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON2000",
                                      "message": "채용 공고 추출 및 저장에 성공했습니다.",
                                      "result": {
                                        "savedToDatabase": true,
                                        "message": "채용 공고 추출 및 저장에 성공했습니다.",
                                        "extracted": {
                                          "companyName": "삼성전자",
                                          "jobTitle": "백엔드 개발자",
                                          "task": "백엔드 서비스 개발 및 운영",
                                          "requirements": "Java/Spring 기반 개발 경험",
                                          "preferredQualifications": "대용량 트래픽 처리 경험",
                                          "rawText": "채용 공고 원문 내용",
                                          "confidence": 0.92
                                        },
                                        "candidates": [
                                          {
                                            "detailClassificationId": 101,
                                            "detailClassificationName": "Java/Spring",
                                            "middleClassificationName": "백엔드",
                                            "bigClassificationName": "개발",
                                            "score": 0.91
                                          }
                                        ],
                                        "classification": {
                                          "detailClassificationId": 101,
                                          "detailClassificationName": "Java/Spring",
                                          "middleClassificationName": "백엔드",
                                          "bigClassificationName": "개발",
                                          "reason": "Spring Boot, JPA, API 개발 맥락이 가장 강합니다.",
                                          "confidence": 0.87
                                        },
                                        "generated": {
                                          "companyName": "삼성전자",
                                          "jobTitle": "Java/Spring 백엔드 개발자",
                                          "task": "백엔드 서비스 개발 및 운영\\nAPI 설계 및 성능 개선",
                                          "requirements": "Java/Spring 기반 개발 경험\\nRDB 사용 경험",
                                          "preferredQualifications": "대용량 트래픽 처리 경험\\nRedis 사용 경험",
                                          "summary": "서비스 백엔드 개발과 운영을 담당할 인재를 찾습니다."
                                        },
                                        "saved": {
                                          "jobPostingId": 10,
                                          "companyId": 3,
                                          "companyName": "삼성전자",
                                          "detailClassificationId": 101,
                                          "detailClassificationName": "Java/Spring",
                                          "task": "백엔드 서비스 개발 및 운영\\nAPI 설계 및 성능 개선",
                                          "requirement": "Java/Spring 기반 개발 경험\\nRDB 사용 경험",
                                          "preferred": "대용량 트래픽 처리 경험\\nRedis 사용 경험"
                                        }
                                      },
                                      "error": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "분류 confidence가 낮아 저장을 보류한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON2000",
                                      "message": "채용 공고 추출 및 저장에 성공했습니다.",
                                      "result": {
                                        "savedToDatabase": false,
                                        "message": "소분류 분류 confidence가 낮아 저장을 보류했습니다.",
                                        "extracted": {
                                          "companyName": "어떤회사",
                                          "jobTitle": "개발자",
                                          "task": "서비스 개발",
                                          "requirements": "개발 경험",
                                          "preferredQualifications": "우대 사항",
                                          "rawText": "채용 공고 원문 내용",
                                          "confidence": 0.79
                                        },
                                        "candidates": [
                                          {
                                            "detailClassificationId": 101,
                                            "detailClassificationName": "Java/Spring",
                                            "middleClassificationName": "백엔드",
                                            "bigClassificationName": "개발",
                                            "score": 0.62
                                          },
                                          {
                                            "detailClassificationId": 102,
                                            "detailClassificationName": "Node.js",
                                            "middleClassificationName": "백엔드",
                                            "bigClassificationName": "개발",
                                            "score": 0.58
                                          }
                                        ],
                                        "classification": {
                                          "detailClassificationId": 101,
                                          "detailClassificationName": "Java/Spring",
                                          "middleClassificationName": "백엔드",
                                          "bigClassificationName": "개발",
                                          "reason": "후보 간 차이가 크지 않습니다.",
                                          "confidence": 0.49
                                        },
                                        "generated": null,
                                        "saved": null
                                      },
                                      "error": null
                                    }
                                    """)
                    )
            )
    })
    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<JobPostingIngestResponse> ingestJobPosting(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @ModelAttribute JobPostingIngestMultipartRequest request
    ) {
        var user = validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "채용 공고 추출 및 저장에 성공했습니다.",
                jobPostingIngestService.ingestAndCreate(user, request)
        );
    }

    @Operation(
            summary = "채용 공고 비동기 일괄 처리 접수",
            description = "이미지 또는 텍스트 공고를 비동기로 추출, 분류, 생성, 저장합니다. 응답으로 받은 taskId로 상태를 조회할 수 있습니다."
    )
    @PostMapping(value = "/ingest/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<JobPostingAsyncSubmitResponse> submitIngestJobPostingAsync(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @ModelAttribute JobPostingIngestMultipartRequest request
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
