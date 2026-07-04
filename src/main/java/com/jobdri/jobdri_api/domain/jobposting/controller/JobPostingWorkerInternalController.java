package com.jobdri.jobdri_api.domain.jobposting.controller;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerContextRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerContextResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerFailureRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerFinalizeRequest;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAsyncFacadeService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingWorkerBridgeService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.InternalApiKeyValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/worker/job-postings")
public class JobPostingWorkerInternalController {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final InternalApiKeyValidator internalApiKeyValidator;
    private final JobPostingWorkerBridgeService jobPostingWorkerBridgeService;
    private final JobPostingAsyncFacadeService jobPostingAsyncFacadeService;

    @PostMapping("/tasks/{taskId}/running")
    public ApiResponse<Void> markRunning(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        jobPostingWorkerBridgeService.markRunning(taskId);
        return ApiResponse.onSuccess("채용 공고 worker 작업 시작 상태를 반영했습니다.");
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ApiResponse<JobPostingIngestResponse> completeTask(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId,
            @Valid @RequestBody JobPostingIngestResponse result
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        return ApiResponse.onSuccess(
                "채용 공고 worker 작업 완료 상태를 반영했습니다.",
                jobPostingWorkerBridgeService.completeTask(taskId, result)
        );
    }

    @PostMapping("/tasks/{taskId}/failed")
    public ApiResponse<Void> failTask(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId,
            @Valid @RequestBody JobPostingWorkerFailureRequest request
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        jobPostingWorkerBridgeService.failTask(taskId, request.errorMessage());
        return ApiResponse.onSuccess("채용 공고 worker 작업 실패 상태를 반영했습니다.");
    }

    @PostMapping("/ingest/context")
    public ApiResponse<JobPostingWorkerContextResponse> getContext(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @Valid @RequestBody JobPostingWorkerContextRequest request
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        return ApiResponse.onSuccess(
                "채용 공고 worker 컨텍스트 조회에 성공했습니다.",
                new JobPostingWorkerContextResponse(
                        jobPostingWorkerBridgeService.createReadableImageUrl(
                                request.userId(),
                                request.imageObjectKey()
                        )
                )
        );
    }

    @PostMapping("/classification/candidates")
    public ApiResponse<List<JobPostingClassificationCandidateResponse>> getCandidates(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @Valid @RequestBody JobPostingExtractResponse extracted
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        return ApiResponse.onSuccess(
                "채용 공고 분류 후보 조회에 성공했습니다.",
                jobPostingWorkerBridgeService.findCandidates(extracted)
        );
    }

    @PostMapping("/ingest/finalize")
    public ApiResponse<JobPostingIngestResponse> finalizeTask(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @Valid @RequestBody JobPostingWorkerFinalizeRequest request
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        return ApiResponse.onSuccess(
                "채용 공고 저장 및 비동기 완료 처리에 성공했습니다.",
                jobPostingWorkerBridgeService.finalizeAndComplete(
                        request.taskId(),
                        request.userId(),
                        request.extracted(),
                        request.candidates(),
                        request.classification(),
                        request.generated()
                )
        );
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<JobPostingAsyncStatusResponse> getTask(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        return ApiResponse.onSuccess(
                "채용 공고 worker 작업 상태 조회에 성공했습니다.",
                jobPostingAsyncFacadeService.getTask(taskId)
        );
    }
}
