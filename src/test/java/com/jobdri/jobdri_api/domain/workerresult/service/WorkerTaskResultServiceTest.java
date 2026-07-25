package com.jobdri.jobdri_api.domain.workerresult.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.workerresult.entity.WorkerTaskResult;
import com.jobdri.jobdri_api.domain.workerresult.entity.WorkerTaskResult.TaskType;
import com.jobdri.jobdri_api.domain.workerresult.repository.WorkerTaskResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerTaskResultServiceTest {

    @Mock
    private WorkerTaskResultRepository workerTaskResultRepository;

    @Test
    @DisplayName("같은 task 결과를 다시 저장하면 새 row 대신 기존 결과를 overwrite한다")
    void upsertGeneratedOverwritesExistingResult() {
        WorkerTaskResult existing = WorkerTaskResult.generated(
                "task-1",
                TaskType.ANALYSIS_COMPLETE,
                "{\"status\":\"first\"}"
        );
        when(workerTaskResultRepository.findById("task-1")).thenReturn(Optional.of(existing));

        WorkerTaskResultService workerTaskResultService = new WorkerTaskResultService(
                workerTaskResultRepository,
                new ObjectMapper()
        );

        workerTaskResultService.upsertGenerated(
                TaskType.ANALYSIS_COMPLETE,
                "task-1",
                Map.of("status", "second")
        );

        assertThat(existing.getStatus()).isEqualTo(WorkerTaskResult.DeliveryStatus.GENERATED);
        assertThat(existing.getResultPayload()).isEqualTo("{\"status\":\"second\"}");
        assertThat(existing.getAttemptCount()).isEqualTo(2);
        verify(workerTaskResultRepository).findById("task-1");
    }

    @Test
    @DisplayName("처음 저장하는 task 결과는 새 row를 생성한다")
    void upsertGeneratedCreatesNewResultWhenMissing() {
        when(workerTaskResultRepository.findById("task-2")).thenReturn(Optional.empty());

        WorkerTaskResultService workerTaskResultService = new WorkerTaskResultService(
                workerTaskResultRepository,
                new ObjectMapper()
        );

        workerTaskResultService.upsertGenerated(
                TaskType.JOB_POSTING_FINALIZE,
                "task-2",
                Map.of("status", "generated")
        );

        verify(workerTaskResultRepository).save(any(WorkerTaskResult.class));
    }
}
