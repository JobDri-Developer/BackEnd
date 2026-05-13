package com.jobdri.jobdri_api.domain.mockapply.service;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
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
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockApplyService {

    private final MockApplyRepository mockApplyRepository;
    private final JobPostingRepository jobPostingRepository;
    private final DetailClassificationRepository detailClassificationRepository;
    private final CompanyRepository companyRepository;

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

        DetailClassification detailClassification = detailClassificationRepository.findById(request.detailClassificationId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.CLASSIFICATION_NOT_FOUND,
                        "해당 소분류를 찾을 수 없습니다. detailClassificationId=" + request.detailClassificationId()
                ));

        JobPosting jobPosting = JobPosting.create(
                company,
                detailClassification,
                normalizeText(request.task()),
                normalizeText(request.requirement()),
                normalizeText(request.preferred())
        );
        JobPosting savedJobPosting = jobPostingRepository.save(jobPosting);

        MockApply mockApply = MockApply.create(user, savedJobPosting, ApplyType.MOCK);
        return MockApplyCreateResponse.from(mockApplyRepository.save(mockApply));
    }

    private String normalizeText(String value) {
        if (StringUtils.hasText(value)) {
            return value;
        }
        return "";
    }
}
