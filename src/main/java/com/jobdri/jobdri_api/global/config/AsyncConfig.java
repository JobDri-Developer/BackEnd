package com.jobdri.jobdri_api.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "jobPostingAsyncExecutor")
    public ThreadPoolTaskExecutor jobPostingAsyncExecutor(
            @Value("${async.job-posting.core-pool-size:2}") int corePoolSize,
            @Value("${async.job-posting.max-pool-size:50}") int maxPoolSize,
            @Value("${async.job-posting.queue-capacity:100}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("job-posting-async-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "mailAsyncExecutor")
    public ThreadPoolTaskExecutor mailAsyncExecutor(
            @Value("${async.mail.core-pool-size:1}") int corePoolSize,
            @Value("${async.mail.max-pool-size:2}") int maxPoolSize,
            @Value("${async.mail.queue-capacity:50}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-async-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "llmAsyncExecutor")
    public ThreadPoolTaskExecutor llmAsyncExecutor(
            @Value("${async.llm.core-pool-size:2}") int corePoolSize,
            @Value("${async.llm.max-pool-size:100}") int maxPoolSize,
            @Value("${async.llm.queue-capacity:200}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("llm-async-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
