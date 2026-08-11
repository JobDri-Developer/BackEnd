package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncCancelResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncSubmitResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.FailureReason;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisAsyncFacadeServiceTest {

    @Mock
    private AnalysisAsyncTaskService analysisAsyncTaskService;

    @Mock
    private AnalysisAsyncProcessor analysisAsyncProcessor;

    @Mock
    private AnalysisService analysisService;

    @Mock
    private UserService userService;

    @InjectMocks
    private AnalysisAsyncFacadeService analysisAsyncFacadeService;

    @Test
    @DisplayName("task 생성 충돌 시 기존 진행 중 작업을 반환하고 추가 처리를 하지 않는다")
    void submitReturnsExistingTaskWhenCreateConflicts() {
        User user = User.signup("테스트 사용자", "analysis-async@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);

        AnalysisAsyncTask existingTask = AnalysisAsyncTask.pending(1L, 10L, 3);

        when(userService.validateUser(user)).thenReturn(user);
        when(analysisService.hasReusableAnalysis(user, 10L)).thenReturn(false);
        when(analysisAsyncTaskService.findActiveTask(1L, 10L)).thenReturn(Optional.empty(), Optional.of(existingTask));
        when(analysisAsyncTaskService.createPendingTask(1L, 10L))
                .thenThrow(new DataIntegrityViolationException("uk_analysis_async_tasks_active_user_mock_apply"));

        AnalysisAsyncSubmitResponse response = analysisAsyncFacadeService.submit(user, 10L);

        assertThat(response.taskId()).isEqualTo(existingTask.getTaskId());
        assertThat(response.status()).isEqualTo("PENDING");
        verify(analysisAsyncProcessor, never()).process(eq(existingTask.getTaskId()), eq(1L), eq(10L), eq(3));
    }

    @Test
    @DisplayName("task 생성 충돌 후에도 진행 중 작업이 없으면 원래 예외를 전파한다")
    void submitPropagatesExceptionWhenTaskDisappearsAfterConflict() {
        User user = User.signup("테스트 사용자", "analysis-async-missing@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("uk_analysis_async_tasks_active_user_mock_apply");

        when(userService.validateUser(user)).thenReturn(user);
        when(analysisService.hasReusableAnalysis(user, 10L)).thenReturn(false);
        when(analysisAsyncTaskService.findActiveTask(1L, 10L)).thenReturn(Optional.empty(), Optional.empty());
        when(analysisAsyncTaskService.createPendingTask(1L, 10L)).thenThrow(exception);

        assertThatThrownBy(() -> analysisAsyncFacadeService.submit(user, 10L))
                .isSameAs(exception);
    }

    @Test
    @DisplayName("메시지 발행이 성공해도 submit 단계에서는 크레딧을 바로 예약하지 않는다")
    void submitDoesNotReserveCreditBeforeWorkerStarts() {
        User user = User.signup("테스트 사용자", "analysis-async-submit@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);

        AnalysisAsyncTask createdTask = AnalysisAsyncTask.pending(1L, 10L, 3);

        when(userService.validateUser(user)).thenReturn(user);
        when(analysisService.hasReusableAnalysis(user, 10L)).thenReturn(false);
        when(analysisAsyncTaskService.findActiveTask(1L, 10L)).thenReturn(Optional.empty());
        when(analysisAsyncTaskService.createPendingTask(1L, 10L)).thenReturn(createdTask);

        AnalysisAsyncSubmitResponse response = analysisAsyncFacadeService.submit(user, 10L);

        assertThat(response.taskId()).isEqualTo(createdTask.getTaskId());
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.cached()).isFalse();
        assertThat(response.resultAvailable()).isFalse();
        verify(analysisAsyncProcessor, times(1)).process(createdTask.getTaskId(), 1L, 10L, 3);
    }

    @Test
    @DisplayName("활성 작업이 없고 동일 입력 분석 결과가 있으면 task를 만들지 않고 즉시 재사용 응답을 반환한다")
    void submitReturnsCachedResponseWhenReusableAnalysisExists() {
        User user = User.signup("테스트 사용자", "analysis-async-cached@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);

        when(userService.validateUser(user)).thenReturn(user);
        when(analysisAsyncTaskService.findActiveTask(1L, 10L)).thenReturn(Optional.empty());
        when(analysisService.hasReusableAnalysis(user, 10L)).thenReturn(true);

        AnalysisAsyncSubmitResponse response = analysisAsyncFacadeService.submit(user, 10L);

        assertThat(response.taskId()).isNull();
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.cached()).isTrue();
        assertThat(response.resultAvailable()).isTrue();
        verify(analysisAsyncTaskService, never()).createPendingTask(1L, 10L);
        verify(analysisAsyncProcessor, never()).process(anyString(), eq(1L), eq(10L), eq(3));
    }

    @Test
    @DisplayName("진행 중 작업이 있으면 캐시 재사용보다 진행 중 작업 응답을 우선한다")
    void submitPrefersActiveTaskOverCachedResult() {
        User user = User.signup("테스트 사용자", "analysis-async-active@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);
        AnalysisAsyncTask existingTask = AnalysisAsyncTask.pending(1L, 10L, 3);

        when(userService.validateUser(user)).thenReturn(user);
        when(analysisAsyncTaskService.findActiveTask(1L, 10L)).thenReturn(Optional.of(existingTask));

        AnalysisAsyncSubmitResponse response = analysisAsyncFacadeService.submit(user, 10L);

        assertThat(response.taskId()).isEqualTo(existingTask.getTaskId());
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.cached()).isFalse();
        assertThat(response.resultAvailable()).isFalse();
        verify(analysisService, never()).hasReusableAnalysis(user, 10L);
    }

    @Test
    @DisplayName("성공한 task 상태 조회는 분석 결과를 함께 반환한다")
    void getTaskReturnsResultWhenSucceeded() {
        User user = User.signup("테스트 사용자", "analysis-async-status-success@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);
        AnalysisAsyncStatusResponse status = AnalysisAsyncStatusResponse.builder()
                .taskId("task-1")
                .mockApplyId(10L)
                .status("SUCCEEDED")
                .message("분석이 완료되었습니다.")
                .build();
        AnalysisResponse result = new AnalysisResponse(
                10L,
                100L,
                MockApplyStatus.COMPLETED,
                1,
                80,
                80,
                80,
                80,
                "완료된 분석입니다.",
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
        );

        when(userService.validateUser(user)).thenReturn(user);
        when(analysisAsyncTaskService.getTaskStatus(1L, "task-1")).thenReturn(status);
        when(analysisService.getAnalysis(user, 10L)).thenReturn(result);

        AnalysisAsyncStatusResponse response = analysisAsyncFacadeService.getTask(user, 10L, "task-1");

        assertThat(response.taskId()).isEqualTo("task-1");
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.result()).isEqualTo(result);
        verify(analysisService, times(1)).getAnalysis(user, 10L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RUNNING", "PENDING", "FAILED", "CANCELLED"})
    @DisplayName("비완료 task 상태 조회는 분석 결과를 조회하지 않는다")
    void getTaskReturnsStatusWithoutResultWhenNotSucceeded(String taskStatus) {
        User user = User.signup("테스트 사용자", "analysis-async-status-" + taskStatus.toLowerCase() + "@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);
        AnalysisAsyncStatusResponse status = AnalysisAsyncStatusResponse.builder()
                .taskId("task-1")
                .mockApplyId(10L)
                .status(taskStatus)
                .message("분석 상태입니다.")
                .build();

        when(userService.validateUser(user)).thenReturn(user);
        when(analysisAsyncTaskService.getTaskStatus(1L, "task-1")).thenReturn(status);

        AnalysisAsyncStatusResponse response = analysisAsyncFacadeService.getTask(user, 10L, "task-1");

        assertThat(response).isEqualTo(status);
        verify(analysisService, never()).getAnalysis(user, 10L);
    }

    @Test
    @DisplayName("task 상태 조회 시 요청 mockApplyId가 다르면 예외를 던진다")
    void getTaskThrowsWhenMockApplyIdDoesNotMatch() {
        User user = User.signup("테스트 사용자", "analysis-async-status-forbidden@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);
        AnalysisAsyncStatusResponse status = AnalysisAsyncStatusResponse.builder()
                .taskId("task-1")
                .mockApplyId(99L)
                .status("RUNNING")
                .message("분석 중입니다.")
                .build();

        when(userService.validateUser(user)).thenReturn(user);
        when(analysisAsyncTaskService.getTaskStatus(1L, "task-1")).thenReturn(status);

        assertThatThrownBy(() -> analysisAsyncFacadeService.getTask(user, 10L, "task-1"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);
        verifyNoInteractions(analysisService);
    }

    @Test
    @DisplayName("task 취소는 검증된 사용자 기준으로 task service에 위임한다")
    void cancelDelegatesToTaskService() {
        User user = User.signup("테스트 사용자", "analysis-async-cancel@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);
        AnalysisAsyncCancelResponse cancelResponse = new AnalysisAsyncCancelResponse(
                "task-1",
                "CANCELLED",
                "분석 작업이 취소되었습니다."
        );

        when(userService.validateUser(user)).thenReturn(user);
        when(analysisAsyncTaskService.cancelTask(1L, 10L, "task-1")).thenReturn(cancelResponse);

        AnalysisAsyncCancelResponse response = analysisAsyncFacadeService.cancel(user, 10L, "task-1");

        assertThat(response).isEqualTo(cancelResponse);
        verify(analysisAsyncTaskService, times(1)).cancelTask(1L, 10L, "task-1");
    }

    @Test
    @DisplayName("재시도 횟수가 maxRetryCount에 도달하면 task를 FAILED로 전환한다")
    void retryAtLimitMarksTaskFailed() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);

        task.markRetryScheduled(FailureReason.INTERNAL_ERROR, "retry-1", 1);
        task.markRetryScheduled(FailureReason.INTERNAL_ERROR, "retry-2", 2);
        task.markRetryScheduled(FailureReason.INTERNAL_ERROR, "retry-3", 3);

        assertThat(task.getStatus()).isEqualTo(AnalysisAsyncTask.TaskStatus.FAILED);
        assertThat(task.getRetryCount()).isEqualTo(3);
    }
}
