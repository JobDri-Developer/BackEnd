package com.jobdri.jobdri_api.domain.mockapply.controller;

import com.jobdri.jobdri_api.domain.mockapply.dto.request.MockApplyCreateActualRequest;
import com.jobdri.jobdri_api.domain.mockapply.dto.request.MockApplyCreateMockFromJobPostingRequest;
import com.jobdri.jobdri_api.domain.mockapply.dto.request.MockApplyCreateMockRequest;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyCreateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.mockapply.service.MockApplyService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mock-applies")
@Tag(name = "MockApply", description = "모의 서류 지원 생성 API")
public class MockApplyController {

    private final MockApplyService mockApplyService;

    @Operation(
            summary = "실제 공고 기반 모의 서류 지원 생성",
            description = "공고 텍스트/URL 추출, 공고 저장, 사용자 확인 및 수정이 선행된 뒤 저장된 채용 공고 ID를 기준으로 로그인 사용자의 ACTUAL 타입 모의 서류 지원을 생성합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "모의 서류 지원 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":true,\"code\":\"COMMON2000\",\"message\":\"모의 서류 지원이 생성되었습니다.\",\"result\":{\"jobPostingId\":1,\"mockApplyId\":10,\"applyType\":\"ACTUAL\"},\"error\":null}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보 누락",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":false,\"code\":\"AUTH_4011\",\"message\":\"인증 정보가 누락되었습니다.\",\"result\":null,\"error\":\"인증 정보가 누락되었습니다.\"}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "채용 공고 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":false,\"code\":\"JOB_POSTING_4041\",\"message\":\"해당 공고를 찾을 수 없습니다. jobPostingId=999\",\"result\":null,\"error\":\"해당 공고를 찾을 수 없습니다. jobPostingId=999\"}")
                    )
            )
    })
    @PostMapping("/actual")
    public ApiResponse<MockApplyCreateResponse> createActualApply(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody MockApplyCreateActualRequest request
    ) {
        return ApiResponse.onSuccess(
                "모의 서류 지원이 생성되었습니다.",
                mockApplyService.createActualApply(userDetails.getUser(), request.jobPostingId())
        );
    }

    @Operation(
            summary = "저장된 공고 기반 MOCK 타입 모의 서류 지원 생성",
            description = "AI 초안 생성 후 사용자가 수정하여 저장한 채용 공고 ID를 기준으로 로그인 사용자의 MOCK 타입 모의 서류 지원을 생성합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "모의 서류 지원 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":true,\"code\":\"COMMON2000\",\"message\":\"모의 서류 지원이 생성되었습니다.\",\"result\":{\"jobPostingId\":1,\"mockApplyId\":10,\"applyType\":\"MOCK\"},\"error\":null}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보 누락",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":false,\"code\":\"AUTH_4011\",\"message\":\"인증 정보가 누락되었습니다.\",\"result\":null,\"error\":\"인증 정보가 누락되었습니다.\"}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "다른 사용자의 공고 접근",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":false,\"code\":\"AUTH_4031\",\"message\":\"해당 공고에 접근할 수 없습니다.\",\"result\":null,\"error\":\"해당 공고에 접근할 수 없습니다.\"}")
                    )
            )
    })
    @PostMapping("/mock/from-job-posting")
    public ApiResponse<MockApplyCreateResponse> createMockApplyFromJobPosting(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody MockApplyCreateMockFromJobPostingRequest request
    ) {
        return ApiResponse.onSuccess(
                "모의 서류 지원이 생성되었습니다.",
                mockApplyService.createMockApplyFromJobPosting(userDetails.getUser(), request.jobPostingId())
        );
    }

    @Operation(
            summary = "가상 공고 기반 모의 서류 지원 생성",
            description = "선택한 소분류를 기준으로 가상 채용 공고와 MOCK 타입 모의 서류 지원을 함께 생성합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "모의 서류 지원 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":true,\"code\":\"COMMON2000\",\"message\":\"모의 서류 지원이 생성되었습니다.\",\"result\":{\"jobPostingId\":1,\"mockApplyId\":10,\"applyType\":\"MOCK\"},\"error\":null}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청값 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":false,\"code\":\"REQ_4002\",\"message\":\"파라미터 형식이 잘못되었습니다.\",\"result\":null,\"error\":[\"[companyId] 회사 ID는 필수입니다. (입력값: null)\"]}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보 누락",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":false,\"code\":\"AUTH_4011\",\"message\":\"인증 정보가 누락되었습니다.\",\"result\":null,\"error\":\"인증 정보가 누락되었습니다.\"}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회사 또는 직무 분류 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "company_not_found", value = "{\"isSuccess\":false,\"code\":\"COMPANY_4041\",\"message\":\"해당 회사를 찾을 수 없습니다. companyId=999\",\"result\":null,\"error\":\"해당 회사를 찾을 수 없습니다. companyId=999\"}"),
                                    @ExampleObject(name = "classification_not_found", value = "{\"isSuccess\":false,\"code\":\"CLASSIFICATION_4041\",\"message\":\"해당 소분류를 찾을 수 없습니다. detailClassificationId=999\",\"result\":null,\"error\":\"해당 소분류를 찾을 수 없습니다. detailClassificationId=999\"}"),
                                    @ExampleObject(name = "middle_detail_mismatch", value = "{\"isSuccess\":false,\"code\":\"CLASSIFICATION_4041\",\"message\":\"해당 소분류가 중분류에 속하지 않습니다. middleClassificationId=999, detailClassificationId=1\",\"result\":null,\"error\":\"해당 소분류가 중분류에 속하지 않습니다. middleClassificationId=999, detailClassificationId=1\"}")
                            }
                    )
            )
    })
    @PostMapping("/mock")
    public ApiResponse<MockApplyCreateResponse> createMockApply(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody MockApplyCreateMockRequest request
    ) {
        return ApiResponse.onSuccess(
                "모의 서류 지원이 생성되었습니다.",
                mockApplyService.createMockApply(userDetails.getUser(), request)
        );
    }

    @Operation(
            summary = "모의 서류 지원의 생성 공고 조회",
            description = "mockApplyId에 연결된 생성 공고를 조회합니다."
    )
    @GetMapping("/{mockApplyId}/job-posting")
    public ApiResponse<JobPostingResponse> getMockApplyJobPosting(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId
    ) {
        return ApiResponse.onSuccess(
                "모의 공고 조회에 성공했습니다.",
                mockApplyService.getMockApplyJobPosting(userDetails.getUser(), mockApplyId)
        );
    }
}
