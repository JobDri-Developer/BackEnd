package com.jobdri.jobdri_api.domain.jobposting.controller;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerContextRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerContextResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerFailureRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerFinalizeRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerResultStoreRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerRetryRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerRunningRequest;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAsyncFacadeService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingWorkerBridgeService;
import com.jobdri.jobdri_api.domain.workerresult.dto.WorkerTaskResultResponse;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.InternalApiKeyValidator;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Hidden
@Tag(name = "JobPosting Worker Internal", description = "채용 공고 worker 내부 통신 API")
public class JobPostingWorkerInternalController {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final InternalApiKeyValidator internalApiKeyValidator;
    private final JobPostingWorkerBridgeService jobPostingWorkerBridgeService;
    private final JobPostingAsyncFacadeService jobPostingAsyncFacadeService;

    @Operation(summary = "채용 공고 worker 작업 실행 상태 반영", description = "worker가 taskId 기준 채용 공고 작업을 실행 중 상태로 변경합니다.")
    @PostMapping("/tasks/{taskId}/running")
    public ApiResponse<Void> markRunning(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId,
            @Valid @RequestBody JobPostingWorkerRunningRequest request
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        jobPostingWorkerBridgeService.markRunning(taskId, request.workerId(), request.retryCount(), request.submittedAt());
        return ApiResponse.onSuccess("채용 공고 worker 작업 시작 상태를 반영했습니다.");
    }

    @Operation(summary = "채용 공고 worker 작업 재시도 상태 반영", description = "worker가 채용 공고 작업 실패 후 재시도 상태와 메타데이터를 반영합니다.")
    @PostMapping("/tasks/{taskId}/retry")
    public ApiResponse<Void> markRetry(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId,
            @Valid @RequestBody JobPostingWorkerRetryRequest request
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        jobPostingWorkerBridgeService.markRetry(
                taskId,
                request.failureReason(),
                request.errorMessage(),
                request.retryCount(),
                request.workerId(),
                request.queueLatencyMillis()
        );
        return ApiResponse.onSuccess("채용 공고 worker 작업 재시도 상태를 반영했습니다.");
    }

    @Operation(summary = "채용 공고 worker 작업 완료 반영", description = "worker가 생성한 채용 공고 결과를 저장하고 taskId 기준 작업 완료 상태를 반영합니다.")
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

    @Operation(summary = "채용 공고 worker 결과 선저장", description = "worker가 finalize 호출 전에 taskId 기준 채용 공고 결과를 durable storage에 저장합니다.")
    @PostMapping("/tasks/{taskId}/result")
    public ApiResponse<Void> storeResult(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId,
            @Valid @RequestBody JobPostingWorkerResultStoreRequest request
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        jobPostingWorkerBridgeService.storeFinalizeResult(taskId, request);
        return ApiResponse.onSuccess("채용 공고 worker 결과 선저장에 성공했습니다.");
    }

    @Operation(summary = "채용 공고 worker 저장 결과 조회", description = "worker가 taskId 기준으로 저장된 채용 공고 결과 payload를 조회합니다.")
    @GetMapping("/tasks/{taskId}/result")
    public ApiResponse<WorkerTaskResultResponse> getStoredResult(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        return ApiResponse.onSuccess(
                "채용 공고 worker 저장 결과 조회에 성공했습니다.",
                jobPostingWorkerBridgeService.getStoredResult(taskId)
        );
    }

    @Operation(summary = "채용 공고 worker 작업 실패 반영", description = "worker가 채용 공고 작업 실패 상태와 실패 메타데이터를 반영합니다.")
    @PostMapping("/tasks/{taskId}/failed")
    public ApiResponse<Void> failTask(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId,
            @Valid @RequestBody JobPostingWorkerFailureRequest request
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        jobPostingWorkerBridgeService.failTask(
                taskId,
                request.failureReason(),
                request.errorMessage(),
                request.retryCount(),
                request.workerId(),
                request.queueLatencyMillis()
        );
        return ApiResponse.onSuccess("채용 공고 worker 작업 실패 상태를 반영했습니다.");
    }

    @Operation(summary = "채용 공고 worker 컨텍스트 조회", description = "worker가 이미지 기반 채용 공고 처리를 위해 읽기 가능한 컨텍스트 정보를 조회합니다.")
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

    @Operation(summary = "채용 공고 분류 후보 조회", description = "추출된 채용 공고 정보를 바탕으로 분류 후보 목록을 조회합니다.")
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

    @Operation(summary = "채용 공고 적재 후처리 및 완료", description = "추출, 분류, 생성 결과를 바탕으로 채용 공고 저장과 비동기 완료 처리를 한 번에 수행합니다.")
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

    @Operation(summary = "채용 공고 worker 작업 상태 조회", description = "taskId 기준 채용 공고 worker 비동기 작업 상태를 내부 용도로 조회합니다.")
    @GetMapping("/tasks/{taskId}")
    public ApiResponse<JobPostingAsyncStatusResponse> getTask(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        return ApiResponse.onSuccess(
                "채용 공고 worker 작업 상태 조회에 성공했습니다.",
                jobPostingAsyncFacadeService.getTaskInternal(taskId)
        );
    }
}
