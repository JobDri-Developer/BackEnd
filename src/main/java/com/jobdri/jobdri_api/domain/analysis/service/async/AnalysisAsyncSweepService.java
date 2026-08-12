package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.jobdri.jobdri_api.domain.analysis.infrastructure.async.AnalysisAsyncTaskSweepCoordinator;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Service
public class AnalysisAsyncSweepService extends AnalysisAsyncTaskSweepCoordinator {

    public AnalysisAsyncSweepService(
            AnalysisAsyncTaskRepository analysisAsyncTaskRepository,
            AnalysisAsyncTaskService analysisAsyncTaskService,
            AnalysisAsyncCreditCoordinator analysisAsyncCreditCoordinator,
            TransactionTemplate transactionTemplate,
            AnalysisQueueProperties analysisQueueProperties,
            Clock clock
    ) {
        super(
                analysisAsyncTaskRepository,
                analysisAsyncTaskService,
                analysisAsyncCreditCoordinator,
                transactionTemplate,
                analysisQueueProperties,
                clock
        );
    }
}
