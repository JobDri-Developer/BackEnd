package com.jobdri.jobdri_api.domain.jobapplication.controller;

import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationCreateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationDetailUpdateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationFromJobPostingRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationPositionRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationSort;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationArchiveItemResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationBoardResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationDetailResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationResponse;
import com.jobdri.jobdri_api.domain.jobapplication.service.JobApplicationArchiveService;
import com.jobdri.jobdri_api.domain.jobapplication.service.JobApplicationBoardService;
import com.jobdri.jobdri_api.domain.jobapplication.service.JobApplicationDetailService;
import com.jobdri.jobdri_api.domain.jobapplication.service.JobApplicationService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-applications")
@Tag(name = "JobApplication", description = "실제 지원관리 카드 API")
public class JobApplicationController {
    private final JobApplicationService jobApplicationService;
    private final UserService userService;
    private final JobApplicationBoardService jobApplicationBoardService;
    private final JobApplicationDetailService jobApplicationDetailService;
    private final JobApplicationArchiveService jobApplicationArchiveService;

    @Operation(summary = "지원관리 칸반 조회", description = "활성 카드를 4개 열로 반환합니다. 회사명·공고명·직무명을 검색하며 count는 검색 결과 기준입니다. CREATED_DESC에서는 드래그를 비활성화합니다.")
    @GetMapping("/board")
    public ApiResponse<JobApplicationBoardResponse> board(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "MANUAL") JobApplicationSort sort
    ) {
        return ApiResponse.onSuccess("지원관리 칸반 조회에 성공했습니다.",
                jobApplicationBoardService.getBoard(validateAuthenticatedUser(userDetails), query, sort));
    }

    @Operation(summary = "지원 카드 이동", description = "카드를 제거한 도착 열의 0 기반 targetIndex에 삽입합니다. 필터링된 화면의 인덱스가 아닌 전체 열 기준이며, 저장 후 전체 MANUAL 보드를 반환합니다.")
    @PatchMapping("/{jobApplicationId}/position")
    public ApiResponse<JobApplicationBoardResponse> move(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long jobApplicationId,
            @Valid @RequestBody JobApplicationPositionRequest request
    ) {
        return ApiResponse.onSuccess("지원 카드 이동에 성공했습니다.",
                jobApplicationBoardService.move(validateAuthenticatedUser(userDetails), jobApplicationId, request));
    }

    @Operation(summary = "지원 카드 보관", description = "활성 보드에서 카드를 제외하고 보관 시각을 기록합니다. 남은 열의 수동 순서는 연속으로 정리합니다.")
    @PostMapping("/{jobApplicationId}/archive")
    public ApiResponse<JobApplicationResponse> archive(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long jobApplicationId
    ) {
        return ApiResponse.onSuccess(
                "지원 카드 보관에 성공했습니다.",
                jobApplicationArchiveService.archive(validateAuthenticatedUser(userDetails), jobApplicationId)
        );
    }

    @Operation(summary = "지원 카드 복원", description = "보관 전 단계를 유지하며 해당 단계의 마지막 순서로 복원합니다.")
    @PostMapping("/{jobApplicationId}/restore")
    public ApiResponse<JobApplicationResponse> restore(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long jobApplicationId
    ) {
        return ApiResponse.onSuccess(
                "지원 카드 복원에 성공했습니다.",
                jobApplicationArchiveService.restore(validateAuthenticatedUser(userDetails), jobApplicationId)
        );
    }

    @Operation(summary = "지원 카드 보관함 조회", description = "보관 시각과 ID 내림차순으로 현재 사용자의 보관 카드를 페이지 조회합니다.")
    @GetMapping("/archive")
    public ApiResponse<Page<JobApplicationArchiveItemResponse>> archivePage(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.onSuccess(
                "지원 카드 보관함 조회에 성공했습니다.",
                jobApplicationArchiveService.getArchive(validateAuthenticatedUser(userDetails), page, size)
        );
    }

    @Operation(summary = "지원 카드 영구 삭제", description = "지원 카드와 카드가 직접 소유한 상세 데이터만 삭제합니다. 출처 공고와 연결된 모의지원은 유지합니다.")
    @DeleteMapping("/{jobApplicationId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long jobApplicationId
    ) {
        jobApplicationArchiveService.delete(validateAuthenticatedUser(userDetails), jobApplicationId);
        return ApiResponse.onSuccess("지원 카드 영구 삭제에 성공했습니다.", null);
    }

    @Operation(summary = "지원 카드 수동 등록", description = "분석을 시작하지 않고 독립적인 지원관리 카드를 생성합니다.")
    @PostMapping
    public ApiResponse<JobApplicationResponse> create(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody JobApplicationCreateRequest request
    ) {
        var user = validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess("지원 카드 등록에 성공했습니다.", jobApplicationService.create(user, request));
    }

    @Operation(summary = "저장 공고에서 지원 카드 등록", description = "내 저장 공고의 현재 값을 복사해 이후 독립적으로 관리되는 지원 카드를 생성합니다.")
    @PostMapping("/from-job-posting")
    public ApiResponse<JobApplicationResponse> createFromJobPosting(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody JobApplicationFromJobPostingRequest request
    ) {
        var user = validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "저장 공고에서 지원 카드 등록에 성공했습니다.",
                jobApplicationService.createFromJobPosting(user, request)
        );
    }

    @Operation(summary = "지원 카드 상세 조회", description = "공고 스냅샷과 체크리스트·정량 스펙·실제 제출용 자기소개서를 전달된 순서대로 조회합니다.")
    @GetMapping("/{jobApplicationId}")
    public ApiResponse<JobApplicationDetailResponse> get(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long jobApplicationId
    ) {
        var user = validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess("지원 카드 상세 조회에 성공했습니다.", jobApplicationDetailService.get(user, jobApplicationId));
    }

    @Operation(summary = "지원 카드 상세 전체 저장", description = "공고 스냅샷과 세 탭 데이터를 한 트랜잭션에서 전체 교체합니다. 마지막 조회 수정 시각이 오래된 경우 409를 반환합니다.")
    @PutMapping("/{jobApplicationId}")
    public ApiResponse<JobApplicationDetailResponse> update(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long jobApplicationId,
            @Valid @RequestBody JobApplicationDetailUpdateRequest request
    ) {
        var user = validateAuthenticatedUser(userDetails);
        return ApiResponse.onSuccess(
                "지원 카드 상세 저장에 성공했습니다.",
                jobApplicationDetailService.update(user, jobApplicationId, request)
        );
    }

    private com.jobdri.jobdri_api.domain.user.entity.User validateAuthenticatedUser(UserDetailsImpl userDetails) {
        return userService.validateUser(userDetails == null ? null : userDetails.getUser());
    }
}
