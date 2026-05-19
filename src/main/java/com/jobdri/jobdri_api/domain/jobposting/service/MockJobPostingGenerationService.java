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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockJobPostingGenerationService {

    private final CompanyRepository companyRepository;
    private final JobPostingAiService jobPostingAiService;
    private final MockQuestionCacheService mockQuestionCacheService;

    public JobPostingMockGenerateResponse generate(JobPostingMockGenerateRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.COMPANY_NOT_FOUND,
                        "해당 회사를 찾을 수 없습니다. companyId=" + request.companyId()
                ));

        JobPostingMockGenerateResponse generatedPosting = jobPostingAiService.generateMockJobPosting(request, company);
        return new JobPostingMockGenerateResponse(
                generatedPosting.companyName(),
                generatedPosting.jobTitle(),
                generatedPosting.task(),
                generatedPosting.requirement(),
                generatedPosting.preferred(),
                generatedPosting.summary(),
                mockQuestionCacheService.getRecommendedQuestions(request)
        );
    }
}
