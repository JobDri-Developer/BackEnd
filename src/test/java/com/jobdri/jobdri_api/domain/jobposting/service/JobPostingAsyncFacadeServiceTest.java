package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncSubmitResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingAsyncFacadeServiceTest {

    @Mock
    private JobPostingAsyncTaskService jobPostingAsyncTaskService;

    @Mock
    private JobPostingAsyncProcessor jobPostingAsyncProcessor;

    @Mock
    private UserService userService;

    @InjectMocks
    private JobPostingAsyncFacadeService jobPostingAsyncFacadeService;

    @Test
    @DisplayName("채용공고 비동기 작업 생성 시 작업 소유자 userId를 함께 저장한다")
    void submitCreatesTaskWithUserId() {
        User user = User.signup("테스트 사용자", "job-posting-submit@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 7L);
        JobPostingIngestRequest request = new JobPostingIngestRequest("공고 원문", null);
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);

        when(userService.validateUser(user)).thenReturn(user);
        when(jobPostingAsyncTaskService.createPendingTask(7L)).thenReturn(task);

        JobPostingAsyncSubmitResponse response = jobPostingAsyncFacadeService.submit(user, request);

        assertThat(response.getTaskId()).isEqualTo(task.getTaskId());
        verify(jobPostingAsyncTaskService).createPendingTask(7L);
        verify(jobPostingAsyncProcessor).process(eq(task.getTaskId()), any(), eq(3));
    }

    @Test
    @DisplayName("일반 사용자 조회는 본인 소유 task만 조회한다")
    void getTaskUsesValidatedUserScope() {
        User user = User.signup("테스트 사용자", "job-posting-owner@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 7L);
        JobPostingAsyncStatusResponse response = JobPostingAsyncStatusResponse.builder()
                .taskId("task-1")
                .status("PENDING")
                .message("진행 중")
                .build();

        when(userService.validateUser(user)).thenReturn(user);
        when(jobPostingAsyncTaskService.getTask(user, "task-1")).thenReturn(response);

        JobPostingAsyncStatusResponse result = jobPostingAsyncFacadeService.getTask(user, "task-1");

        assertThat(result.getTaskId()).isEqualTo("task-1");
        verify(jobPostingAsyncTaskService).getTask(user, "task-1");
    }

    @Test
    @DisplayName("내부 조회는 사용자 검증 없이 taskId로 조회한다")
    void getTaskInternalReadsByTaskId() {
        JobPostingAsyncStatusResponse response = JobPostingAsyncStatusResponse.builder()
                .taskId("task-1")
                .status("SUCCEEDED")
                .message("완료")
                .build();

        when(jobPostingAsyncTaskService.getTask("task-1")).thenReturn(response);

        JobPostingAsyncStatusResponse result = jobPostingAsyncFacadeService.getTaskInternal("task-1");

        assertThat(result.getTaskId()).isEqualTo("task-1");
        verify(jobPostingAsyncTaskService).getTask("task-1");
    }

    @Test
    @DisplayName("메시지 발행 실패 시 생성했던 task를 삭제하고 접수 실패를 반환한다")
    void submitDeletesTaskWhenPublishFails() {
        User user = User.signup("테스트 사용자", "job-posting-submit-fail@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 7L);
        JobPostingIngestRequest request = new JobPostingIngestRequest("공고 원문", null);
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);

        when(userService.validateUser(user)).thenReturn(user);
        when(jobPostingAsyncTaskService.createPendingTask(7L)).thenReturn(task);
        doThrow(new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, "publish failed"))
                .when(jobPostingAsyncProcessor)
                .process(eq(task.getTaskId()), any(), eq(3));

        assertThatThrownBy(() -> jobPostingAsyncFacadeService.submit(user, request))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);

        verify(jobPostingAsyncTaskService, times(1)).deleteTask(task.getTaskId());
    }
}
