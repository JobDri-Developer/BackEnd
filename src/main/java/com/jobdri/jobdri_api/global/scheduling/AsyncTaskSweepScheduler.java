package com.jobdri.jobdri_api.global.scheduling;

import com.jobdri.jobdri_api.domain.analysis.service.async.AnalysisAsyncSweepService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAsyncTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!analysis-eval")
@RequiredArgsConstructor
public class AsyncTaskSweepScheduler {

    private final JobPostingAsyncTaskService jobPostingAsyncTaskService;
    private final AnalysisAsyncSweepService analysisAsyncSweepService;

    @Scheduled(
            fixedDelayString = "${app.async-task-sweep.fixed-delay-millis:60000}",
            initialDelayString = "${app.async-task-sweep.initial-delay-millis:30000}"
    )
    public void sweepTimedOutTasks() {
        int expiredJobPostingTasks = sweepJobPostingTasks();
        int expiredAnalysisTasks = sweepAnalysisTasks();

        if (expiredJobPostingTasks > 0 || expiredAnalysisTasks > 0) {
            log.info(
                    "Async task sweep expired tasks. jobPostingExpired={} analysisExpired={}",
                    expiredJobPostingTasks,
                    expiredAnalysisTasks
            );
        }
    }

    private int sweepJobPostingTasks() {
        try {
            return jobPostingAsyncTaskService.sweepTimedOutTasks();
        } catch (RuntimeException e) {
            log.error("Job posting async task sweep failed.", e);
            return 0;
        }
    }

    private int sweepAnalysisTasks() {
        try {
            return analysisAsyncSweepService.sweepTimedOutTasks();
        } catch (RuntimeException e) {
            log.error("Analysis async task sweep failed.", e);
            return 0;
        }
    }
}
