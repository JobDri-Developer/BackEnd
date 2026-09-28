package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisCreditService;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncCreditStatus;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.metrics.AsyncMetricsRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AnalysisAsyncCreditCoordinator {
    private final AnalysisCreditService analysisCreditService;
    private final UserService userService;
    private final AsyncMetricsRecorder asyncMetricsRecorder;

    public AnalysisAsyncCreditCoordinator(
            AnalysisCreditService analysisCreditService,
            UserService userService,
            AsyncMetricsRecorder asyncMetricsRecorder
    ) {
        this.analysisCreditService = analysisCreditService;
        this.userService = userService;
        this.asyncMetricsRecorder = asyncMetricsRecorder;
    }

    public boolean releaseReservedCreditIfNeeded(AnalysisAsyncTask task) {
        if (task.getCreditStatus() != AnalysisAsyncCreditStatus.RESERVED || task.getCreditReferenceId() == null) {
            return false;
        }

        User user = userService.getUser(task.getUserId());
        analysisCreditService.refund(user, task.getCreditReferenceId());
        boolean changed = task.markCreditReleased();
        recordTransitionAfterCommit("released", changed ? "success" : "ignored");
        return changed;
    }

    public boolean reserveCreditIfNeeded(AnalysisAsyncTask task) {
        if (!task.canReserveCredit()) {
            return false;
        }

        User user = userService.getUser(task.getUserId());
        String creditReferenceId = analysisCreditService.createAsyncReferenceId(
                task.getTaskId(),
                task.nextCreditReferenceVersion()
        );
        analysisCreditService.deduct(user, creditReferenceId);
        boolean changed = task.markCreditReserved(creditReferenceId);
        recordTransitionAfterCommit("reserved", changed ? "success" : "ignored");
        return changed;
    }

    public boolean confirmReservedCreditIfNeeded(AnalysisAsyncTask task) {
        boolean changed = task.markCreditConfirmed();
        recordTransitionAfterCommit("confirmed", changed ? "success" : "ignored");
        return changed;
    }

    private void recordTransitionAfterCommit(String transition, String outcome) {
        Runnable recorder = () -> asyncMetricsRecorder.incrementCreditTransition(transition, outcome);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recorder.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recorder.run();
            }
        });
    }
}
