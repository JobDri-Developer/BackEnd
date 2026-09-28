package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisCreditService;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncCreditStatus;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.metrics.AsyncMetricsRecorder;
import org.springframework.stereotype.Service;

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
        asyncMetricsRecorder.incrementCreditTransition("released", changed ? "success" : "ignored");
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
        asyncMetricsRecorder.incrementCreditTransition("reserved", changed ? "success" : "ignored");
        return changed;
    }

    public boolean confirmReservedCreditIfNeeded(AnalysisAsyncTask task) {
        boolean changed = task.markCreditConfirmed();
        asyncMetricsRecorder.incrementCreditTransition("confirmed", changed ? "success" : "ignored");
        return changed;
    }
}
