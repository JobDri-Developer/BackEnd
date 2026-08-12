package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.jobdri.jobdri_api.domain.analysis.infrastructure.async.AnalysisAsyncTaskSweepCoordinator;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisCreditService;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Service
public class AnalysisAsyncSweepService extends AnalysisAsyncTaskSweepCoordinator {

    public AnalysisAsyncSweepService(
            AnalysisAsyncTaskRepository analysisAsyncTaskRepository,
            AnalysisAsyncTaskService analysisAsyncTaskService,
            AnalysisCreditService analysisCreditService,
            UserService userService,
            TransactionTemplate transactionTemplate,
            AnalysisQueueProperties analysisQueueProperties,
            Clock clock
    ) {
        super(
                analysisAsyncTaskRepository,
                analysisAsyncTaskService,
                analysisCreditService,
                userService,
                transactionTemplate,
                analysisQueueProperties,
                clock
        );
    }
}
