package com.jobdri.jobdri_api.domain.jobposting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingAsyncTaskServiceTest {

    @Mock
    private JobPostingAsyncTaskRepository jobPostingAsyncTaskRepository;

    private JobPostingAsyncTaskService jobPostingAsyncTaskService;

    @BeforeEach
    void setUp() {
        jobPostingAsyncTaskService = new JobPostingAsyncTaskService(jobPostingAsyncTaskRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("일반 사용자는 본인 소유 task만 조회할 수 있다")
    void getTaskAllowsOwnerOnly() {
        User user = User.signup("테스트 사용자", "job-posting-owner-task@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 7L);
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L);

        when(jobPostingAsyncTaskRepository.findByTaskIdAndUserId(task.getTaskId(), 7L)).thenReturn(Optional.of(task));

        var response = jobPostingAsyncTaskService.getTask(user, task.getTaskId());

        assertThat(response.getTaskId()).isEqualTo(task.getTaskId());
        verify(jobPostingAsyncTaskRepository).findByTaskIdAndUserId(task.getTaskId(), 7L);
    }

    @Test
    @DisplayName("일반 사용자는 다른 사람 task를 조회할 수 없다")
    void getTaskRejectsNonOwner() {
        User user = User.signup("테스트 사용자", "job-posting-other-task@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 7L);

        when(jobPostingAsyncTaskRepository.findByTaskIdAndUserId("task-1", 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobPostingAsyncTaskService.getTask(user, "task-1"))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("관리자는 소유자와 관계없이 모든 task를 조회할 수 있다")
    void getTaskAllowsAdmin() {
        User admin = User.signup("관리자", "admin-task@example.com", "encoded-password");
        ReflectionTestUtils.setField(admin, "id", 99L);
        admin.promoteToAdmin();
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L);

        when(jobPostingAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        var response = jobPostingAsyncTaskService.getTask(admin, task.getTaskId());

        assertThat(response.getTaskId()).isEqualTo(task.getTaskId());
        verify(jobPostingAsyncTaskRepository).findById(task.getTaskId());
    }
}
