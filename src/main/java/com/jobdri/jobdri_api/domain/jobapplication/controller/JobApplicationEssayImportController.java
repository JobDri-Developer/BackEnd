package com.jobdri.jobdri_api.domain.jobapplication.controller;

import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationEssaySourceResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationEssayPreviewResponse;
import com.jobdri.jobdri_api.domain.jobapplication.service.JobApplicationEssayImportService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-applications/{targetId}/essay-sources")
@Tag(name = "JobApplication", description = "실제 지원관리 카드 API")
public class JobApplicationEssayImportController {
    private final JobApplicationEssayImportService service;

    @GetMapping
    @Operation(summary = "문항을 가져올 내 공고 검색", description = "현재 공고와 문항이 없는 공고를 제외합니다. 최근 등록순이며 기본적으로 보관 공고를 제외하고 includeArchived=true이면 포함합니다. 저장하지 않습니다.")
    public ApiResponse<Page<JobApplicationEssaySourceResponse>> sources(
            @AuthenticationPrincipal UserDetailsImpl principal, @PathVariable Long targetId,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.onSuccess("문항 가져오기 공고 조회에 성공했습니다.",
                service.sources(principal == null ? null : principal.getUser(), targetId, query, includeArchived, page, size));
    }

    @GetMapping("/{sourceId}")
    @Operation(summary = "원본 공고의 자기소개서 질문·답변 조회", description = "본인 공고의 문항을 저장된 순서로 반환합니다. essayId는 원본 식별자이며 복사본 ID가 아닙니다. 저장하지 않습니다.")
    public ApiResponse<List<JobApplicationEssayPreviewResponse>> previews(
            @AuthenticationPrincipal UserDetailsImpl principal, @PathVariable Long targetId,
            @PathVariable Long sourceId) {
        return ApiResponse.onSuccess("문항 가져오기 미리보기에 성공했습니다.",
                service.previews(principal == null ? null : principal.getUser(), targetId, sourceId));
    }
}
