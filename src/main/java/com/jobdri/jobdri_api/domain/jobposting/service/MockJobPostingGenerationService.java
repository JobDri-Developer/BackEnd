package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingMockGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockGenerateResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockJobPostingGenerationService {

    private final CompanyRepository companyRepository;
    private final JobPostingAiAsyncService jobPostingAiAsyncService;

    public JobPostingMockGenerateResponse generate(JobPostingMockGenerateRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.COMPANY_NOT_FOUND,
                        "해당 회사를 찾을 수 없습니다. companyId=" + request.companyId()
                ));

        CompletableFuture<JobPostingMockGenerateResponse> generatedPostingFuture =
                jobPostingAiAsyncService.generateMockJobPosting(request, company);
        CompletableFuture<java.util.List<String>> recommendedQuestionsFuture =
                jobPostingAiAsyncService.getRecommendedQuestions(request);

        CompletableFuture.allOf(generatedPostingFuture, recommendedQuestionsFuture).join();
        JobPostingMockGenerateResponse generatedPosting = generatedPostingFuture.join();

        return new JobPostingMockGenerateResponse(
                generatedPosting.companyName(),
                generatedPosting.jobTitle(),
                generatedPosting.task(),
                generatedPosting.requirement(),
                generatedPosting.preferred(),
                generatedPosting.summary(),
                recommendedQuestionsFuture.join()
        );
    }
}
