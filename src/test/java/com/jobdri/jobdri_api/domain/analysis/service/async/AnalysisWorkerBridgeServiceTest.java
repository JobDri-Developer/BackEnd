package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisWorkerCompleteRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisWorkerResultStoreRequest;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.FailureReason;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisExecutionPayload;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisService;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.domain.workerresult.entity.WorkerTaskResult.TaskType;
import com.jobdri.jobdri_api.domain.workerresult.service.WorkerTaskResultService;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisWorkerBridgeServiceTest {

    @Mock
    private AnalysisAsyncTaskService analysisAsyncTaskService;

    @Mock
    private AnalysisAsyncTaskRepository analysisAsyncTaskRepository;

    @Mock
    private AnalysisService analysisService;

    @Mock
    private UserService userService;

    @Mock
    private WorkerTaskResultService workerTaskResultService;

    @InjectMocks
    private AnalysisWorkerBridgeService analysisWorkerBridgeService;

    @Test
    @DisplayName("취소된 task의 complete 요청은 결과 저장과 성공 처리를 하지 않는다")
    void completeTaskRejectsCancelledTaskWithoutSideEffects() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);
        task.requestCancel();
        AnalysisWorkerCompleteRequest request = new AnalysisWorkerCompleteRequest(
                1L,
                10L,
                mock(AnalysisLlmResponse.class),
                "worker-1",
                10L
        );

        when(analysisAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> analysisWorkerBridgeService.completeTask(task.getTaskId(), request))
                .isInstanceOf(GeneralException.class);

        verify(workerTaskResultService, never()).upsertGenerated(eq(TaskType.ANALYSIS_COMPLETE), eq(task.getTaskId()), any());
        verify(analysisService, never()).finalizeAnalysis(any(), eq(10L), any(), any());
        verify(analysisAsyncTaskService, never()).markSuccess(eq(task.getTaskId()), any());
    }

    @Test
    @DisplayName("취소된 task의 결과 선저장은 결과 저장을 하지 않는다")
    void storeGeneratedResultRejectsCancelledTaskWithoutUpsert() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);
        task.requestCancel();
        AnalysisWorkerResultStoreRequest request = new AnalysisWorkerResultStoreRequest(
                1L,
                10L,
                mock(AnalysisLlmResponse.class)
        );

        when(analysisAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> analysisWorkerBridgeService.storeGeneratedResult(task.getTaskId(), request))
                .isInstanceOf(GeneralException.class);

        verify(workerTaskResultService, never()).upsertGenerated(eq(TaskType.ANALYSIS_COMPLETE), eq(task.getTaskId()), any());
    }

    @Test
    @DisplayName("취소된 task는 worker 컨텍스트 조회 시 크레딧을 예약하지 않는다")
    void getContextRejectsCancelledTaskWithoutCreditReservation() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);
        task.requestCancel();

        when(analysisAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> analysisWorkerBridgeService.getContext(task.getTaskId(), 1L, 10L))
                .isInstanceOf(GeneralException.class);

        verify(analysisService, never()).deductAnalysisCredit(any(), anyString());
        verify(analysisAsyncTaskService, never()).markCreditReserved(anyString(), anyString());
    }

    @Test
    @DisplayName("worker가 컨텍스트를 조회할 때 처음 한 번만 크레딧을 예약한다")
    void getContextReservesCreditBeforePreparingExecution() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);
        User user = User.signup("테스트 사용자", "analysis-worker@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);

        JobPosting jobPosting = mock(JobPosting.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        Company company = mock(Company.class);
        when(company.getName()).thenReturn("잡드리");
        when(jobPosting.getCompany()).thenReturn(company);
        when(jobPosting.getTask()).thenReturn("백엔드 개발");
        when(jobPosting.getRequirement()).thenReturn("Spring");
        when(jobPosting.getPreferred()).thenReturn("RabbitMQ");
        when(jobPosting.getDetailClassification().getDetailName()).thenReturn("백엔드");
        when(jobPosting.getDetailClassification().getMiddleClassification().getMiddleName()).thenReturn("서버");
        when(jobPosting.getDetailClassification().getMiddleClassification().getClassification().getBigName()).thenReturn("개발");

        AnalysisExecutionPayload payload = new AnalysisExecutionPayload(
                1L,
                10L,
                jobPosting,
                List.of(),
                List.of()
        );

        when(analysisAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));
        when(userService.getUser(1L)).thenReturn(user);
        when(analysisService.prepareAnalysisExecution(user, 10L)).thenReturn(payload);

        analysisWorkerBridgeService.getContext(task.getTaskId(), 1L, 10L);

        verify(analysisService).deductAnalysisCredit(user, "analysisTaskId=" + task.getTaskId());
        verify(analysisAsyncTaskService).markCreditReserved(task.getTaskId(), "analysisTaskId=" + task.getTaskId());
        verify(analysisService).prepareAnalysisExecution(user, 10L);
    }

    @Test
    @DisplayName("이미 예약된 작업은 컨텍스트 재조회 시 크레딧을 다시 예약하지 않는다")
    void getContextDoesNotReserveCreditTwice() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);
        ReflectionTestUtils.invokeMethod(task, "markCreditReserved", "analysisTaskId=" + task.getTaskId());
        User user = User.signup("테스트 사용자", "analysis-worker-repeat@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);

        JobPosting jobPosting = mock(JobPosting.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        Company company = mock(Company.class);
        when(company.getName()).thenReturn("잡드리");
        when(jobPosting.getCompany()).thenReturn(company);
        when(jobPosting.getTask()).thenReturn("백엔드 개발");
        when(jobPosting.getRequirement()).thenReturn("Spring");
        when(jobPosting.getPreferred()).thenReturn("RabbitMQ");
        when(jobPosting.getDetailClassification().getDetailName()).thenReturn("백엔드");
        when(jobPosting.getDetailClassification().getMiddleClassification().getMiddleName()).thenReturn("서버");
        when(jobPosting.getDetailClassification().getMiddleClassification().getClassification().getBigName()).thenReturn("개발");

        AnalysisExecutionPayload payload = new AnalysisExecutionPayload(
                1L,
                10L,
                jobPosting,
                List.of(),
                List.of()
        );

        when(analysisAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));
        when(userService.getUser(1L)).thenReturn(user);
        when(analysisService.prepareAnalysisExecution(user, 10L)).thenReturn(payload);

        analysisWorkerBridgeService.getContext(task.getTaskId(), 1L, 10L);

        verify(analysisService, never()).deductAnalysisCredit(eq(user), anyString());
        verify(analysisAsyncTaskService, never()).markCreditReserved(eq(task.getTaskId()), anyString());
        verify(analysisService).prepareAnalysisExecution(user, 10L);
    }

    @Test
    @DisplayName("완료된 작업은 running이나 retry로 되돌리지 않는다")
    void terminalTaskDoesNotReopen() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);
        task.markSuccess();

        when(analysisAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        analysisWorkerBridgeService.markRunning(task.getTaskId(), "worker-1", 1, java.time.Instant.now());
        analysisWorkerBridgeService.markRetry(
                task.getTaskId(),
                FailureReason.INTERNAL_ERROR,
                "retry",
                1,
                "worker-1",
                10L
        );

        verify(analysisAsyncTaskService, never()).markRunning(eq(task.getTaskId()), eq("worker-1"), eq(1), any());
        verify(analysisAsyncTaskService, never()).markRetryScheduled(eq(task.getTaskId()), eq(FailureReason.INTERNAL_ERROR), eq("retry"), eq(1));
    }

    @Test
    @DisplayName("완료 요청의 사용자나 mockApply가 task와 다르면 거부한다")
    void completeTaskRejectsMismatchedIdentity() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);
        when(analysisAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        AnalysisWorkerCompleteRequest request = new AnalysisWorkerCompleteRequest(
                2L,
                11L,
                mock(com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse.class),
                "worker-1",
                10L
        );

        assertThatThrownBy(() -> analysisWorkerBridgeService.completeTask(task.getTaskId(), request))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("complete 전에 분석 결과를 durable storage에 선저장할 수 있다")
    void storeGeneratedResultPersistsPayload() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);
        when(analysisAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        AnalysisWorkerResultStoreRequest request = new AnalysisWorkerResultStoreRequest(
                1L,
                10L,
                mock(com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse.class)
        );

        analysisWorkerBridgeService.storeGeneratedResult(task.getTaskId(), request);

        verify(workerTaskResultService).upsertGenerated(TaskType.ANALYSIS_COMPLETE, task.getTaskId(), request);
    }

    @Test
    @DisplayName("이미 종료된 task에도 분석 결과 선저장은 no-op 성격으로 허용한다")
    void storeGeneratedResultAllowsTerminalTask() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);
        task.markSuccess();
        when(analysisAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        AnalysisWorkerResultStoreRequest request = new AnalysisWorkerResultStoreRequest(
                1L,
                10L,
                mock(com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse.class)
        );

        analysisWorkerBridgeService.storeGeneratedResult(task.getTaskId(), request);

        verify(workerTaskResultService).upsertGenerated(TaskType.ANALYSIS_COMPLETE, task.getTaskId(), request);
    }

    @Test
    @DisplayName("이미 성공한 complete 재호출도 결과 전달 완료 상태로 마킹한다")
    void completeTaskMarksDeliveredForSucceededTask() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);
        task.markSuccess();
        User user = User.signup("테스트 사용자", "analysis-complete@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);
        var llmResponse = mock(com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse.class);

        when(analysisAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));
        when(userService.getUser(1L)).thenReturn(user);
        when(analysisService.getAnalysis(user, 10L)).thenReturn(mock(com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse.class));

        AnalysisWorkerCompleteRequest request = new AnalysisWorkerCompleteRequest(
                1L,
                10L,
                llmResponse,
                "worker-1",
                10L
        );

        analysisWorkerBridgeService.completeTask(task.getTaskId(), request);

        InOrder inOrder = inOrder(workerTaskResultService);
        inOrder.verify(workerTaskResultService).upsertGenerated(
                TaskType.ANALYSIS_COMPLETE,
                task.getTaskId(),
                new AnalysisWorkerResultStoreRequest(1L, 10L, llmResponse)
        );
        inOrder.verify(workerTaskResultService).markDeliveredIfPresent(TaskType.ANALYSIS_COMPLETE, task.getTaskId());
    }
}
