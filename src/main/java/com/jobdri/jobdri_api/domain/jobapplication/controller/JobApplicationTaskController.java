package com.jobdri.jobdri_api.domain.jobapplication.controller;

import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationTaskRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationTaskResponse;
import com.jobdri.jobdri_api.domain.jobapplication.service.JobApplicationTaskService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-applications/{jobApplicationId}/tasks")
@Tag(name = "JobApplication", description = "실제 지원관리 카드 API")
public class JobApplicationTaskController {
    private final JobApplicationTaskService service;

    /** Lists tasks for the authenticated owner of the card. */
    @GetMapping
    @Operation(summary = "공고별 할 일 목록 조회")
    public ApiResponse<List<JobApplicationTaskResponse>> list(
            @AuthenticationPrincipal UserDetailsImpl principal, @PathVariable Long jobApplicationId) {
        return ApiResponse.onSuccess("할 일 목록 조회에 성공했습니다.", service.list(user(principal), jobApplicationId));
    }

    /** Reads a task scoped to the card in the request path. */
    @GetMapping("/{taskId}")
    @Operation(summary = "공고별 할 일 상세 조회")
    public ApiResponse<JobApplicationTaskResponse> get(
            @AuthenticationPrincipal UserDetailsImpl principal, @PathVariable Long jobApplicationId,
            @PathVariable Long taskId) {
        return ApiResponse.onSuccess("할 일 조회에 성공했습니다.", service.get(user(principal), jobApplicationId, taskId));
    }

    /** Creates an incomplete task on the specified card. */
    @PostMapping
    @Operation(summary = "공고별 할 일 등록")
    public ApiResponse<JobApplicationTaskResponse> create(
            @AuthenticationPrincipal UserDetailsImpl principal, @PathVariable Long jobApplicationId,
            @Valid @RequestBody JobApplicationTaskRequest request) {
        return ApiResponse.onSuccess("할 일 등록에 성공했습니다.", service.create(user(principal), jobApplicationId, request));
    }

    /** Replaces editable task fields while preserving completion. */
    @PutMapping("/{taskId}")
    @Operation(summary = "공고별 할 일 수정", description = "기한·메모·하위 체크리스트를 요청 값으로 교체합니다. 할 일의 완료 상태는 유지합니다.")
    public ApiResponse<JobApplicationTaskResponse> update(
            @AuthenticationPrincipal UserDetailsImpl principal, @PathVariable Long jobApplicationId,
            @PathVariable Long taskId, @Valid @RequestBody JobApplicationTaskRequest request) {
        return ApiResponse.onSuccess("할 일 수정에 성공했습니다.", service.update(user(principal), jobApplicationId, taskId, request));
    }

    /** Sets task completion independently of its checklist. */
    @PatchMapping("/{taskId}/completion")
    @Operation(summary = "공고별 할 일 완료 상태 설정", description = "요청한 완료 상태로 설정합니다. 하위 체크리스트 상태는 유지합니다.")
    public ApiResponse<JobApplicationTaskResponse> setCompleted(
            @AuthenticationPrincipal UserDetailsImpl principal, @PathVariable Long jobApplicationId,
            @PathVariable Long taskId, @Valid @RequestBody JobApplicationTaskRequest.Completion request) {
        return ApiResponse.onSuccess("할 일 완료 상태를 변경했습니다.",
                service.setCompleted(user(principal), jobApplicationId, taskId, request.completed()));
    }

    /** Deletes the specified task and its checklist. */
    @DeleteMapping("/{taskId}")
    @Operation(summary = "공고별 할 일 삭제")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserDetailsImpl principal,
                                    @PathVariable Long jobApplicationId, @PathVariable Long taskId) {
        service.delete(user(principal), jobApplicationId, taskId);
        return ApiResponse.onSuccess("할 일 삭제에 성공했습니다.", null);
    }

    /** Extracts the user for service-level authentication. */
    private User user(UserDetailsImpl principal) {
        return principal == null ? null : principal.getUser();
    }
}
