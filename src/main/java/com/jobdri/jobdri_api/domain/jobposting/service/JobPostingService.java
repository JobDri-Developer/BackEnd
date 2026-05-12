package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingUpdateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final CompanyRepository companyRepository;
    private final DetailClassificationRepository detailClassificationRepository;

    @Transactional
    public JobPostingResponse createJobPosting(JobPostingCreateRequest request) {
        Company company = findOrCreateCompany(request.companyName(), request.companySize());
        DetailClassification detailClassification = findDetailClassification(request.detailClassificationId());

        JobPosting jobPosting = JobPosting.create(
                company,
                detailClassification,
                request.task(),
                request.requirement(),
                request.preferred()
        );

        return JobPostingResponse.from(jobPostingRepository.save(jobPosting));
    }

    @Transactional
    public JobPostingResponse updateJobPosting(Long jobPostingId, JobPostingUpdateRequest request) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_POSTING_NOT_FOUND,
                        "해당 공고를 찾을 수 없습니다. jobPostingId=" + jobPostingId
                ));

        Company company = findOrCreateCompany(request.companyName(), request.companySize());
        DetailClassification detailClassification = findDetailClassification(request.detailClassificationId());

        jobPosting.update(
                company,
                detailClassification,
                request.task(),
                request.requirement(),
                request.preferred()
        );

        return JobPostingResponse.from(jobPosting);
    }

    public JobPostingResponse getJobPosting(Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_POSTING_NOT_FOUND,
                        "해당 공고를 찾을 수 없습니다. jobPostingId=" + jobPostingId
                ));

        return JobPostingResponse.from(jobPosting);
    }

    public List<JobPostingResponse> getAllJobPostings() {
        return jobPostingRepository.findAll().stream()
                .map(JobPostingResponse::from)
                .toList();
    }

    public List<JobPostingResponse> getJobPostingsByCompany(Long companyId) {
        return jobPostingRepository.findAllByCompanyId(companyId).stream()
                .map(JobPostingResponse::from)
                .toList();
    }

    private Company findOrCreateCompany(String companyName, com.jobdri.jobdri_api.domain.company.entity.CompanySize companySize) {
        return companyRepository.findByName(companyName)
                .orElseGet(() -> companyRepository.save(Company.create(companyName, companySize)));
    }

    private DetailClassification findDetailClassification(Long detailClassificationId) {
        return detailClassificationRepository.findById(detailClassificationId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.CLASSIFICATION_NOT_FOUND,
                        "해당 소분류를 찾을 수 없습니다. detailClassificationId=" + detailClassificationId
                ));
    }
}
