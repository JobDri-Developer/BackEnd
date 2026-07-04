package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private AnalysisWorkerBridgeService analysisWorkerBridgeService;

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

        verify(analysisService).reserveAnalysisCredit(user, "analysisTaskId=" + task.getTaskId());
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

        verify(analysisService, never()).reserveAnalysisCredit(eq(user), anyString());
        verify(analysisAsyncTaskService, never()).markCreditReserved(eq(task.getTaskId()), anyString());
        verify(analysisService).prepareAnalysisExecution(user, 10L);
    }
}
