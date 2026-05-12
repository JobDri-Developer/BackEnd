package com.jobdri.jobdri_api.domain.applicationdraft.controller;

import com.jobdri.jobdri_api.domain.applicationdraft.dto.request.ApplicationDraftUpsertRequest;
import com.jobdri.jobdri_api.domain.applicationdraft.dto.response.ApplicationDraftResponse;
import com.jobdri.jobdri_api.domain.applicationdraft.dto.response.ApplicationDraftSaveResponse;
import com.jobdri.jobdri_api.domain.applicationdraft.service.ApplicationDraftService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/application-drafts")
@Tag(name = "Application Draft", description = "지원 플로우 임시저장 API")
public class ApplicationDraftController {

    private final ApplicationDraftService applicationDraftService;

    @Operation(
            summary = "내 임시저장 생성/수정",
            description = "로그인한 사용자의 지원 플로우 임시저장을 생성하거나 수정합니다. 사용자당 최근 임시저장 1개만 유지합니다."
    )
    @PutMapping("/me")
    public ApiResponse<ApplicationDraftSaveResponse> saveOrUpdateMyDraft(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody ApplicationDraftUpsertRequest request
    ) {
        return ApiResponse.onSuccess(
                "임시저장되었습니다.",
                applicationDraftService.saveOrUpdate(getCurrentUser(userDetails), request)
        );
    }

    @Operation(
            summary = "내 임시저장 조회",
            description = "로그인한 사용자의 최근 임시저장 1건을 조회합니다. 임시저장이 없으면 result를 null로 반환합니다."
    )
    @GetMapping("/me")
    public ApiResponse<ApplicationDraftResponse> getMyDraft(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                null,
                applicationDraftService.getMyDraft(getCurrentUser(userDetails))
        );
    }

    @Operation(
            summary = "내 임시저장 삭제",
            description = "로그인한 사용자의 임시저장을 삭제합니다. 임시저장이 없어도 성공 처리합니다."
    )
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteMyDraft(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        applicationDraftService.deleteMyDraft(getCurrentUser(userDetails));
        return ApiResponse.onSuccess("임시저장이 삭제되었습니다.");
    }

    private User getCurrentUser(UserDetailsImpl userDetails) {
        if (userDetails == null) {
            throw new GeneralException(GeneralErrorCode.MISSING_AUTH_INFO);
        }
        return userDetails.getUser();
    }
}
