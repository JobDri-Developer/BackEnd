package com.jobdri.jobdri_api.domain.mockapply.controller;

import com.jobdri.jobdri_api.domain.mockapply.dto.request.MockApplyCreateActualRequest;
import com.jobdri.jobdri_api.domain.mockapply.dto.request.MockApplyCreateMockFromJobPostingRequest;
import com.jobdri.jobdri_api.domain.mockapply.dto.request.MockApplyCreateMockRequest;
import com.jobdri.jobdri_api.domain.mockapply.dto.request.MockApplyUpdateNameRequest;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyCreateResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyHomeResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyRetryResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplySequenceResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyUpdateNameResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
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
            summary = "내 모의 서류 지원 홈 목록 조회",
            description = "홈 화면에서 이어서 작성할 지원과 완료된 분석 결과 카드를 조회합니다."
    )
    @GetMapping("/me")
    public ApiResponse<MockApplyHomeResponse> getMyMockApplies(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "모의 서류 지원 목록 조회에 성공했습니다.",
                mockApplyService.getMyMockApplies(userDetails.getUser())
        );
    }

    @Operation(
            summary = "모의 서류 지원 단건 삭제",
            description = "mockApplyId에 해당하는 모의 서류 지원과 연결된 문항, 분석 결과만 삭제합니다. 같은 공고의 다른 모의 지원은 유지됩니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "모의 서류 지원 삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":true,\"code\":\"COMMON2000\",\"message\":\"모의 서류 지원이 삭제되었습니다.\",\"result\":null,\"error\":null}")
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
                    description = "다른 사용자의 모의 서류 지원 접근",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":false,\"code\":\"AUTH_4031\",\"message\":\"해당 모의 서류 지원에 접근할 수 없습니다.\",\"result\":null,\"error\":\"해당 모의 서류 지원에 접근할 수 없습니다.\"}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "모의 서류 지원 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":false,\"code\":\"MOCK_APPLY_4041\",\"message\":\"해당 모의 서류 지원을 찾을 수 없습니다. mockApplyId=999\",\"result\":null,\"error\":\"해당 모의 서류 지원을 찾을 수 없습니다. mockApplyId=999\"}")
                    )
            )
    })
    @DeleteMapping("/{mockApplyId}")
    public ApiResponse<Void> deleteMockApply(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId
    ) {
        mockApplyService.deleteMockApply(userDetails.getUser(), mockApplyId);
        return ApiResponse.onSuccess("모의 서류 지원이 삭제되었습니다.", null);
    }

    @Operation(
            summary = "모의 서류 지원 이름 변경",
            description = "대시보드 카드에서 표시할 모의 서류 지원 이름을 변경합니다."
    )
    @PatchMapping("/{mockApplyId}/name")
    public ApiResponse<MockApplyUpdateNameResponse> updateMockApplyName(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId,
            @Valid @RequestBody MockApplyUpdateNameRequest request
    ) {
        return ApiResponse.onSuccess(
                "모의 서류 지원 이름이 변경되었습니다.",
                mockApplyService.updateMockApplyName(userDetails.getUser(), mockApplyId, request.name())
        );
    }

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
                            examples = @ExampleObject(value = "{\"isSuccess\":true,\"code\":\"COMMON2000\",\"message\":\"모의 서류 지원이 생성되었습니다.\",\"result\":{\"jobPostingId\":1,\"mockApplyId\":10,\"applyType\":\"ACTUAL\",\"sequence\":1},\"error\":null}")
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
                mockApplyService.createActualApply(userDetails.getUser(), request.jobPostingId(), request.sequence())
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
                            examples = @ExampleObject(value = "{\"isSuccess\":true,\"code\":\"COMMON2000\",\"message\":\"모의 서류 지원이 생성되었습니다.\",\"result\":{\"jobPostingId\":1,\"mockApplyId\":10,\"applyType\":\"MOCK\",\"sequence\":1},\"error\":null}")
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
                mockApplyService.createMockApplyFromJobPosting(
                        userDetails.getUser(),
                        request.jobPostingId(),
                        request.sequence()
                )
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
                            examples = @ExampleObject(value = "{\"isSuccess\":true,\"code\":\"COMMON2000\",\"message\":\"모의 서류 지원이 생성되었습니다.\",\"result\":{\"jobPostingId\":1,\"mockApplyId\":10,\"applyType\":\"MOCK\",\"sequence\":1},\"error\":null}")
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
            summary = "모의 서류 지원 재도전",
            description = "기존 모의 서류 지원의 공고와 선택 문항을 복사해 새 회차의 모의 서류 지원을 생성합니다. 답변은 비워진 상태로 자소서 입력 단계부터 다시 진행합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "재도전 모의 서류 지원 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":true,\"code\":\"COMMON2000\",\"message\":\"재도전 모의 서류 지원이 생성되었습니다.\",\"result\":{\"sourceMockApplyId\":10,\"jobPostingId\":2,\"mockApplyId\":11,\"applyType\":\"MOCK\",\"status\":\"ANSWER_WRITE\",\"sequence\":2},\"error\":null}")
                    )
            )
    })
    @PostMapping("/{mockApplyId}/retry")
    public ApiResponse<MockApplyRetryResponse> retryMockApply(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId
    ) {
        return ApiResponse.onSuccess(
                "재도전 모의 서류 지원이 생성되었습니다.",
                mockApplyService.retryMockApply(userDetails.getUser(), mockApplyId)
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

    @Operation(
            summary = "같은 공고 기준 자소서 개수 및 순번 조회",
            description = "현재 mockApply가 연결된 채용 공고 기준으로, 로그인 사용자가 생성한 자소서 총 개수와 현재 자소서 순번을 조회합니다."
    )
    @GetMapping("/{mockApplyId}/sequence")
    public ApiResponse<MockApplySequenceResponse> getMockApplySequence(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId
    ) {
        return ApiResponse.onSuccess(
                "자소서 순번 조회에 성공했습니다.",
                mockApplyService.getMockApplySequence(userDetails.getUser(), mockApplyId)
        );
    }
}
