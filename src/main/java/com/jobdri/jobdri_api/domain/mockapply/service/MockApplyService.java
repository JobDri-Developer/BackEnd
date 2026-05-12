package com.jobdri.jobdri_api.domain.mockapply.service;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
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

    private static final String VIRTUAL_COMPANY_NAME = "가상 기업";
    private static final CompanySize VIRTUAL_COMPANY_SIZE = CompanySize.STARTUP;

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
        DetailClassification detailClassification = detailClassificationRepository.findById(request.detailClassificationId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.CLASSIFICATION_NOT_FOUND,
                        "해당 소분류를 찾을 수 없습니다. detailClassificationId=" + request.detailClassificationId()
                ));

        Company company = companyRepository.findByName(VIRTUAL_COMPANY_NAME)
                .orElseGet(() -> companyRepository.save(Company.create(VIRTUAL_COMPANY_NAME, VIRTUAL_COMPANY_SIZE)));

        JobPosting jobPosting = JobPosting.create(
                company,
                detailClassification,
                resolveTask(request.task(), detailClassification),
                resolveRequirement(request.requirement(), detailClassification),
                resolvePreferred(request.preferred(), detailClassification)
        );
        JobPosting savedJobPosting = jobPostingRepository.save(jobPosting);

        MockApply mockApply = MockApply.create(user, savedJobPosting, ApplyType.MOCK);
        return MockApplyCreateResponse.from(mockApplyRepository.save(mockApply));
    }

    private String resolveTask(String task, DetailClassification detailClassification) {
        if (StringUtils.hasText(task)) {
            return task;
        }
        return detailClassification.getDetailName() + " 직무 기반 가상 주요 업무를 수행합니다.";
    }

    private String resolveRequirement(String requirement, DetailClassification detailClassification) {
        if (StringUtils.hasText(requirement)) {
            return requirement;
        }
        return detailClassification.getDetailName() + " 직무 수행에 필요한 기본 자격 요건을 갖춥니다.";
    }

    private String resolvePreferred(String preferred, DetailClassification detailClassification) {
        if (StringUtils.hasText(preferred)) {
            return preferred;
        }
        return detailClassification.getDetailName() + " 직무 관련 경험과 역량을 우대합니다.";
    }
}
