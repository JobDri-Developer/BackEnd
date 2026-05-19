package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingMockGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockGenerateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class JobPostingAiAsyncService {

    private final JobPostingAiService jobPostingAiService;
    private final MockQuestionCacheService mockQuestionCacheService;

    @Async("llmAsyncExecutor")
    public CompletableFuture<JobPostingMockGenerateResponse> generateMockJobPosting(
            JobPostingMockGenerateRequest request,
            Company company
    ) {
        return CompletableFuture.completedFuture(
                jobPostingAiService.generateMockJobPosting(request, company)
        );
    }

    @Async("llmAsyncExecutor")
    public CompletableFuture<List<String>> getRecommendedQuestions(JobPostingMockGenerateRequest request) {
        return CompletableFuture.completedFuture(
                mockQuestionCacheService.getRecommendedQuestions(request)
        );
    }
}
