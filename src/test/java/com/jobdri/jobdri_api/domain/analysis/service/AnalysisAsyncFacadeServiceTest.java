package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncSubmitResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verify;
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

        AnalysisAsyncTask existingTask = AnalysisAsyncTask.pending(1L, 10L);

        when(userService.validateUser(user)).thenReturn(user);
        when(analysisAsyncTaskService.findActiveTask(1L, 10L)).thenReturn(Optional.empty(), Optional.of(existingTask));
        when(analysisAsyncTaskService.createPendingTask(1L, 10L))
                .thenThrow(new DataIntegrityViolationException("uk_analysis_async_tasks_active_user_mock_apply"));

        AnalysisAsyncSubmitResponse response = analysisAsyncFacadeService.submit(user, 10L);

        assertThat(response.taskId()).isEqualTo(existingTask.getTaskId());
        assertThat(response.status()).isEqualTo("PENDING");
        verify(analysisService, never()).reserveAnalysisCredit(eq(user), anyString());
        verify(analysisAsyncProcessor, never()).process(eq(existingTask.getTaskId()), eq(1L), eq(10L), anyString());
    }

    @Test
    @DisplayName("task 생성 충돌 후에도 진행 중 작업이 없으면 원래 예외를 전파한다")
    void submitPropagatesExceptionWhenTaskDisappearsAfterConflict() {
        User user = User.signup("테스트 사용자", "analysis-async-missing@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("uk_analysis_async_tasks_active_user_mock_apply");

        when(userService.validateUser(user)).thenReturn(user);
        when(analysisAsyncTaskService.findActiveTask(1L, 10L)).thenReturn(Optional.empty(), Optional.empty());
        when(analysisAsyncTaskService.createPendingTask(1L, 10L)).thenThrow(exception);

        assertThatThrownBy(() -> analysisAsyncFacadeService.submit(user, 10L))
                .isSameAs(exception);
    }
}
