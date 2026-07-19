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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
}
