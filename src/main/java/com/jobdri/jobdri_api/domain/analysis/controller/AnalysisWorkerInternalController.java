package com.jobdri.jobdri_api.domain.analysis.controller;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisWorkerCompleteRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisWorkerContextRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisWorkerContextResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisWorkerFailureRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisWorkerResultStoreRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisWorkerRetryRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisWorkerRunningRequest;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisAsyncTaskService;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisWorkerBridgeService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/worker/analysis")
@Hidden
@Tag(name = "Analysis Worker Internal", description = "자소서 분석 worker 내부 통신 API")
public class AnalysisWorkerInternalController {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final InternalApiKeyValidator internalApiKeyValidator;
    private final AnalysisWorkerBridgeService analysisWorkerBridgeService;
    private final AnalysisAsyncTaskService analysisAsyncTaskService;

    @Operation(summary = "자소서 분석 worker 작업 실행 상태 반영", description = "worker가 taskId 기준 자소서 분석 작업을 실행 중 상태로 변경합니다.")
    @PostMapping("/tasks/{taskId}/running")
    public ApiResponse<Void> markRunning(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId,
            @Valid @RequestBody AnalysisWorkerRunningRequest request
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        analysisWorkerBridgeService.markRunning(taskId, request.workerId(), request.retryCount(), request.submittedAt());
        return ApiResponse.onSuccess("자소서 분석 worker 작업 시작 상태를 반영했습니다.");
    }

    @Operation(summary = "자소서 분석 worker 작업 재시도 상태 반영", description = "worker가 자소서 분석 작업 실패 후 재시도 상태와 메타데이터를 반영합니다.")
    @PostMapping("/tasks/{taskId}/retry")
    public ApiResponse<Void> markRetry(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId,
            @Valid @RequestBody AnalysisWorkerRetryRequest request
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        analysisWorkerBridgeService.markRetry(
                taskId,
                request.failureReason(),
                request.errorMessage(),
                request.retryCount(),
                request.workerId(),
                request.queueLatencyMillis()
        );
        return ApiResponse.onSuccess("자소서 분석 worker 작업 재시도 상태를 반영했습니다.");
    }

    @Operation(summary = "자소서 분석 worker 작업 실패 반영", description = "worker가 자소서 분석 작업 실패 상태와 실패 메타데이터를 반영합니다.")
    @PostMapping("/tasks/{taskId}/failed")
    public ApiResponse<Void> failTask(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId,
            @Valid @RequestBody AnalysisWorkerFailureRequest request
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        analysisWorkerBridgeService.failTask(
                taskId,
                request.failureReason(),
                request.errorMessage(),
                request.retryCount(),
                request.workerId(),
                request.queueLatencyMillis()
        );
        return ApiResponse.onSuccess("자소서 분석 worker 작업 실패 상태를 반영했습니다.");
    }

    @Operation(summary = "자소서 분석 worker 컨텍스트 조회", description = "worker가 분석 실행에 필요한 자소서, 공고, 지원 정보 컨텍스트를 조회합니다.")
    @PostMapping("/context")
    public ApiResponse<AnalysisWorkerContextResponse> getContext(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @Valid @RequestBody AnalysisWorkerContextRequest request
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        return ApiResponse.onSuccess(
                "자소서 분석 worker 컨텍스트 조회에 성공했습니다.",
                analysisWorkerBridgeService.getContext(request.taskId(), request.userId(), request.mockApplyId())
        );
    }

    @Operation(summary = "자소서 분석 worker 작업 완료 반영", description = "worker가 생성한 분석 결과를 저장하고 taskId 기준 작업 완료 상태를 반영합니다.")
    @PostMapping("/tasks/{taskId}/complete")
    public ApiResponse<AnalysisResponse> completeTask(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId,
            @Valid @RequestBody AnalysisWorkerCompleteRequest request
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        return ApiResponse.onSuccess(
                "자소서 분석 worker 작업 완료 상태를 반영했습니다.",
                analysisWorkerBridgeService.completeTask(taskId, request)
        );
    }

    @Operation(summary = "자소서 분석 worker 결과 선저장", description = "worker가 complete 호출 전에 taskId 기준 분석 결과를 durable storage에 저장합니다.")
    @PostMapping("/tasks/{taskId}/result")
    public ApiResponse<Void> storeResult(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId,
            @Valid @RequestBody AnalysisWorkerResultStoreRequest request
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        analysisWorkerBridgeService.storeGeneratedResult(taskId, request);
        return ApiResponse.onSuccess("자소서 분석 worker 결과 선저장에 성공했습니다.");
    }

    @Operation(summary = "자소서 분석 worker 저장 결과 조회", description = "worker가 taskId 기준으로 저장된 분석 결과 payload를 조회합니다.")
    @GetMapping("/tasks/{taskId}/result")
    public ApiResponse<WorkerTaskResultResponse> getStoredResult(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        return ApiResponse.onSuccess(
                "자소서 분석 worker 저장 결과 조회에 성공했습니다.",
                analysisWorkerBridgeService.getStoredResult(taskId)
        );
    }

    @Operation(summary = "자소서 분석 worker 작업 상태 조회", description = "taskId 기준 자소서 분석 worker 비동기 작업 상태를 내부 용도로 조회합니다.")
    @GetMapping("/tasks/{taskId}")
    public ApiResponse<AnalysisAsyncStatusResponse> getTask(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String internalApiKey,
            @PathVariable String taskId
    ) {
        internalApiKeyValidator.validate(internalApiKey);
        return ApiResponse.onSuccess(
                "자소서 분석 worker 작업 상태 조회에 성공했습니다.",
                analysisAsyncTaskService.getTaskStatusByTaskId(taskId)
        );
    }
}
