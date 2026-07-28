package com.jobdri.jobdri_api.domain.analysis.controller;

import com.jobdri.jobdri_api.domain.analysis.dto.request.AnalysisRetrievalPreviewRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisRetrievalPreviewResponse;
import com.jobdri.jobdri_api.domain.analysis.service.debug.AnalysisAdminDebugService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/analysis")
@Tag(name = "AnalysisAdmin", description = "관리자용 자소서 분석 디버그 API")
public class AnalysisAdminController {

    private final AnalysisAdminDebugService analysisAdminDebugService;

    @Operation(
            summary = "분석 retrieval 미리보기",
            description = "mockApplyId를 기준으로 실제 분석 전에 조회되는 유사 JD/문항 검색 결과를 반환합니다."
    )
    @PostMapping("/retrieval-preview")
    public ApiResponse<AnalysisRetrievalPreviewResponse> previewRetrieval(
            @Valid @RequestBody AnalysisRetrievalPreviewRequest request
    ) {
        return ApiResponse.onSuccess(
                "분석 retrieval 미리보기에 성공했습니다.",
                analysisAdminDebugService.previewRetrieval(request.mockApplyId())
        );
    }
}
