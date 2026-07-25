package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationResultResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerFinalizeRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerResultStoreRequest;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.workerresult.entity.WorkerTaskResult.TaskType;
import com.jobdri.jobdri_api.domain.workerresult.service.WorkerTaskResultService;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingWorkerBridgeServiceTest {

    @Mock
    private JobPostingAsyncTaskService jobPostingAsyncTaskService;

    @Mock
    private JobPostingAsyncTaskRepository jobPostingAsyncTaskRepository;

    @Mock
    private JobPostingImageStorageService jobPostingImageStorageService;

    @Mock
    private JobPostingClassificationService jobPostingClassificationService;

    @Mock
    private JobPostingService jobPostingService;

    @Mock
    private com.jobdri.jobdri_api.domain.user.service.UserService userService;

    @Mock
    private WorkerTaskResultService workerTaskResultService;

    @InjectMocks
    private JobPostingWorkerBridgeService jobPostingWorkerBridgeService;

    @Test
    @DisplayName("채용 공고 finalize 결과를 durable storage에 선저장할 수 있다")
    void storeFinalizeResultPersistsPayload() {
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(1L, 3);
        when(jobPostingAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        JobPostingWorkerFinalizeRequest result = new JobPostingWorkerFinalizeRequest(
                task.getTaskId(),
                1L,
                mock(JobPostingExtractResponse.class),
                List.of(mock(JobPostingClassificationCandidateResponse.class)),
                mock(JobPostingClassificationResultResponse.class),
                mock(JobPostingGenerateResponse.class)
        );

        jobPostingWorkerBridgeService.storeFinalizeResult(
                task.getTaskId(),
                new JobPostingWorkerResultStoreRequest(1L, result)
        );

        verify(workerTaskResultService).upsertGenerated(TaskType.JOB_POSTING_FINALIZE, task.getTaskId(), result);
    }

    @Test
    @DisplayName("채용 공고 complete 성공 시 저장 결과를 DELIVERED로 마킹한다")
    void completeTaskMarksDelivered() {
        JobPostingIngestResponse result = mock(JobPostingIngestResponse.class);
        when(jobPostingAsyncTaskService.markSuccess("task-1", result)).thenReturn(result);

        jobPostingWorkerBridgeService.completeTask("task-1", result);

        verify(workerTaskResultService).upsertGenerated(TaskType.JOB_POSTING_COMPLETE, "task-1", result);
        verify(workerTaskResultService).markDeliveredIfPresent(TaskType.JOB_POSTING_COMPLETE, "task-1");
    }

    @Test
    @DisplayName("채용 공고 finalize 입력이 유효하지 않으면 worker 결과 저장과 성공 처리를 하지 않는다")
    void finalizeAndCompleteRejectsInvalidInputBeforePersistingWorkerResult() {
        JobPostingExtractResponse extracted = new JobPostingExtractResponse(
                "미분류 회사",
                "string",
                "string",
                "string",
                "",
                "양식에 맞지 않는 입력",
                0.9
        );
        JobPostingGenerateResponse generated = new JobPostingGenerateResponse(
                "잡드리",
                "백엔드 개발자",
                "정제된 주요 업무",
                "정제된 자격 요건",
                "",
                ""
        );

        assertThatThrownBy(() -> jobPostingWorkerBridgeService.finalizeAndComplete(
                "task-1",
                1L,
                extracted,
                List.of(mock(JobPostingClassificationCandidateResponse.class)),
                mock(JobPostingClassificationResultResponse.class),
                generated
        ))
                .isInstanceOf(GeneralException.class)
                .hasMessageContaining("채용 공고로 인식할 수 없는 입력입니다.");

        verifyNoInteractions(
                workerTaskResultService,
                jobPostingAsyncTaskService,
                jobPostingAsyncTaskRepository,
                jobPostingService,
                userService
        );
    }
}
