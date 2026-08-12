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
        User user = User.signup("테스트 사용자", "analysis-credit-release@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);

        when(userService.getUser(1L)).thenReturn(user);
        when(analysisCreditService.createAsyncReferenceId(task.getTaskId(), 1))
                .thenReturn("analysisTaskId=" + task.getTaskId() + ":creditVersion=1");
        analysisAsyncCreditCoordinator.reserveCreditIfNeeded(task);

        boolean released = analysisAsyncCreditCoordinator.releaseReservedCreditIfNeeded(task);

        assertThat(released).isTrue();
        assertThat(task.getCreditStatus()).isEqualTo(AnalysisAsyncCreditStatus.RELEASED);
        verify(userService, org.mockito.Mockito.times(2)).getUser(1L);
        verify(analysisCreditService).refund(user, "analysisTaskId=" + task.getTaskId() + ":creditVersion=1");
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

    @Test
    @DisplayName("크레딧 재예약은 version을 올린 새 reference로 한 번만 수행한다")
    void reserveCreditIfNeededUsesIncrementedReferenceVersion() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);
        User user = User.signup("테스트 사용자", "analysis-credit-reserve@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);

        when(userService.getUser(1L)).thenReturn(user);
        when(analysisCreditService.createAsyncReferenceId(task.getTaskId(), 1))
                .thenReturn("analysisTaskId=" + task.getTaskId() + ":creditVersion=1");
        when(analysisCreditService.createAsyncReferenceId(task.getTaskId(), 2))
                .thenReturn("analysisTaskId=" + task.getTaskId() + ":creditVersion=2");

        boolean firstReserved = analysisAsyncCreditCoordinator.reserveCreditIfNeeded(task);
        boolean secondReserved = analysisAsyncCreditCoordinator.reserveCreditIfNeeded(task);
        boolean released = analysisAsyncCreditCoordinator.releaseReservedCreditIfNeeded(task);
        boolean thirdReserved = analysisAsyncCreditCoordinator.reserveCreditIfNeeded(task);

        assertThat(firstReserved).isTrue();
        assertThat(secondReserved).isFalse();
        assertThat(released).isTrue();
        assertThat(thirdReserved).isTrue();
        assertThat(task.getCreditStatus()).isEqualTo(AnalysisAsyncCreditStatus.RESERVED);
        assertThat(task.getCreditReferenceId()).isEqualTo("analysisTaskId=" + task.getTaskId() + ":creditVersion=2");
        verify(analysisCreditService).deduct(user, "analysisTaskId=" + task.getTaskId() + ":creditVersion=1");
        verify(analysisCreditService).refund(user, "analysisTaskId=" + task.getTaskId() + ":creditVersion=1");
        verify(analysisCreditService).deduct(user, "analysisTaskId=" + task.getTaskId() + ":creditVersion=2");
    }

    @Test
    @DisplayName("예약된 크레딧만 confirm 되고 이후 중복 confirm은 무시된다")
    void confirmReservedCreditIfNeededIsIdempotent() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);
        User user = User.signup("테스트 사용자", "analysis-credit-confirm@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userService.getUser(1L)).thenReturn(user);
        when(analysisCreditService.createAsyncReferenceId(task.getTaskId(), 1))
                .thenReturn("analysisTaskId=" + task.getTaskId() + ":creditVersion=1");

        analysisAsyncCreditCoordinator.reserveCreditIfNeeded(task);

        boolean firstConfirmed = analysisAsyncCreditCoordinator.confirmReservedCreditIfNeeded(task);
        boolean secondConfirmed = analysisAsyncCreditCoordinator.confirmReservedCreditIfNeeded(task);

        assertThat(firstConfirmed).isTrue();
        assertThat(secondConfirmed).isFalse();
        assertThat(task.getCreditStatus()).isEqualTo(AnalysisAsyncCreditStatus.CONFIRMED);
    }
}
