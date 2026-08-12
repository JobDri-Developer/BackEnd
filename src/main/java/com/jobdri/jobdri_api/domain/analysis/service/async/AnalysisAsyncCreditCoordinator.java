package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisCreditService;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncCreditStatus;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class AnalysisAsyncCreditCoordinator {
    private final AnalysisCreditService analysisCreditService;
    private final UserService userService;

    public AnalysisAsyncCreditCoordinator(
            AnalysisCreditService analysisCreditService,
            UserService userService
    ) {
        this.analysisCreditService = analysisCreditService;
        this.userService = userService;
    }

    public boolean releaseReservedCreditIfNeeded(AnalysisAsyncTask task) {
        if (task.getCreditStatus() != AnalysisAsyncCreditStatus.RESERVED || task.getCreditReferenceId() == null) {
            return false;
        }

        User user = userService.getUser(task.getUserId());
        analysisCreditService.refund(user, task.getCreditReferenceId());
        task.markCreditReleased();
        return true;
    }
}
