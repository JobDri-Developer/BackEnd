package com.jobdri.jobdri_api.domain.mockapply.service;

import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingService;
import com.jobdri.jobdri_api.domain.jobposting.service.MockJobPostingGenerationService;
import com.jobdri.jobdri_api.domain.mockapply.dto.request.MockApplyCreateMockRequest;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyCreateResponse;
import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockApplyService {
    private final MockApplyRepository mockApplyRepository;
    private final JobPostingRepository jobPostingRepository;
    private final CompanyRepository companyRepository;
    private final MockJobPostingGenerationService mockJobPostingGenerationService;
    private final JobPostingService jobPostingService;

    @Transactional
    public MockApplyCreateResponse createActualApply(User user, Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_POSTING_NOT_FOUND,
                        "해당 공고를 찾을 수 없습니다. jobPostingId=" + jobPostingId
                ));

        MockApply mockApply = MockApply.create(user, jobPosting, ApplyType.ACTUAL);
        return MockApplyCreateResponse.from(mockApplyRepository.save(mockApply));
    }

    @Transactional
    public MockApplyCreateResponse createMockApply(User user, MockApplyCreateMockRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.COMPANY_NOT_FOUND,
                        "해당 회사를 찾을 수 없습니다. companyId=" + request.companyId()
                ));

        JobPostingMockGenerateResponse generated =
                mockJobPostingGenerationService.generate(request.toJobPostingMockGenerateRequest());

        JobPostingCreateRequest createRequest = new JobPostingCreateRequest(
                company.getName(),
                company.getSize(),
                request.detailClassificationId(),
                generated.task(),
                generated.requirement(),
                generated.preferred()
        );
        Long savedJobPostingId = jobPostingService.createJobPosting(createRequest).getJobPostingId();
        JobPosting savedJobPosting = jobPostingRepository.findById(savedJobPostingId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_POSTING_NOT_FOUND,
                        "생성된 모의 공고를 찾을 수 없습니다. jobPostingId=" + savedJobPostingId
                ));

        MockApply mockApply = MockApply.create(user, savedJobPosting, ApplyType.MOCK);
        return MockApplyCreateResponse.from(mockApplyRepository.save(mockApply));
    }

    public JobPostingResponse getMockApplyJobPosting(User user, Long mockApplyId) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        return JobPostingResponse.from(mockApply.getJobPosting());
    }

    private MockApply getOwnedMockApply(User user, Long mockApplyId) {
        MockApply mockApply = mockApplyRepository.findById(mockApplyId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.MOCK_APPLY_NOT_FOUND,
                        "해당 모의 서류 지원을 찾을 수 없습니다. mockApplyId=" + mockApplyId
                ));

        if (!mockApply.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 모의 서류 지원에 접근할 수 없습니다.");
        }

        return mockApply;
    }
}
