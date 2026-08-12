package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisCreditService;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncCreditStatus;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisAsyncCreditCoordinatorTest {

    @Mock
    private AnalysisCreditService analysisCreditService;

    @Mock
    private UserService userService;

    @InjectMocks
    private AnalysisAsyncCreditCoordinator analysisAsyncCreditCoordinator;

    @Test
    @DisplayName("예약된 크레딧이 있으면 환불 후 RELEASED 상태로 전이한다")
    void releaseReservedCreditIfNeededRefundsReservedCredit() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);
        ReflectionTestUtils.invokeMethod(task, "markCreditReserved", "analysisTaskId=" + task.getTaskId());
        User user = User.signup("테스트 사용자", "analysis-credit-release@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);

        when(userService.getUser(1L)).thenReturn(user);

        boolean released = analysisAsyncCreditCoordinator.releaseReservedCreditIfNeeded(task);

        assertThat(released).isTrue();
        assertThat(task.getCreditStatus()).isEqualTo(AnalysisAsyncCreditStatus.RELEASED);
        verify(userService).getUser(1L);
        verify(analysisCreditService).refund(user, "analysisTaskId=" + task.getTaskId());
    }

    @Test
    @DisplayName("예약된 크레딧이 없으면 환불을 수행하지 않는다")
    void releaseReservedCreditIfNeededSkipsWhenNoReservedCredit() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);

        boolean released = analysisAsyncCreditCoordinator.releaseReservedCreditIfNeeded(task);

        assertThat(released).isFalse();
        verify(userService, never()).getUser(any());
        verify(analysisCreditService, never()).refund(any(), anyString());
    }
}
