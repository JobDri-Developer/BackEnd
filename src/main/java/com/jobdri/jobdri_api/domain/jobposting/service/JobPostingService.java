package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.audit.service.AuditLogService;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingUpdateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
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
    private final UserService userService;
    private final AuditLogService auditLogService;

    @Transactional
    public JobPostingResponse createJobPosting(User user, JobPostingCreateRequest request) {
        User validatedUser = userService.validateUser(user);
        Company company = findOrCreateCompany(request.companyName(), request.companySize());
        DetailClassification detailClassification = findDetailClassification(request.detailClassificationId());

        JobPosting jobPosting = JobPosting.create(
                validatedUser,
                company,
                detailClassification,
                request.task(),
                request.requirement(),
                request.preferred()
        );

        JobPosting savedJobPosting = jobPostingRepository.save(jobPosting);
        JobPostingResponse response = JobPostingResponse.from(savedJobPosting);
        auditLogService.record(
                validatedUser,
                "JOB_POSTING_CREATE",
                "JOB_POSTING",
                savedJobPosting.getId(),
                null,
                JobPostingAuditValue.from(savedJobPosting)
        );

        return response;
    }

    @Transactional
    public JobPostingResponse updateJobPosting(User user, Long jobPostingId, JobPostingUpdateRequest request) {
        User validatedUser = userService.validateUser(user);
        JobPosting jobPosting = getOwnedJobPosting(validatedUser, jobPostingId);
        JobPostingAuditValue beforeValue = JobPostingAuditValue.from(jobPosting);

        Company company = findOrCreateCompany(request.companyName(), request.companySize());
        DetailClassification detailClassification = findDetailClassification(request.detailClassificationId());

        jobPosting.update(
                validatedUser,
                company,
                detailClassification,
                request.task(),
                request.requirement(),
                request.preferred()
        );
        auditLogService.record(
                validatedUser,
                "JOB_POSTING_UPDATE",
                "JOB_POSTING",
                jobPosting.getId(),
                beforeValue,
                JobPostingAuditValue.from(jobPosting)
        );

        return JobPostingResponse.from(jobPosting);
    }

    public JobPostingResponse getJobPosting(User user, Long jobPostingId) {
        User validatedUser = userService.validateUser(user);
        return JobPostingResponse.from(getOwnedJobPosting(validatedUser, jobPostingId));
    }

    public List<JobPostingResponse> getAllJobPostings(User user) {
        User validatedUser = userService.validateUser(user);
        return jobPostingRepository.findAllByUserId(validatedUser.getId()).stream()
                .map(JobPostingResponse::from)
                .toList();
    }

    public List<JobPostingResponse> getJobPostingsByCompany(User user, Long companyId) {
        User validatedUser = userService.validateUser(user);
        return jobPostingRepository.findAllByUserIdAndCompanyId(validatedUser.getId(), companyId).stream()
                .map(JobPostingResponse::from)
                .toList();
    }

    public JobPosting getOwnedJobPosting(User user, Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_POSTING_NOT_FOUND,
                        "해당 공고를 찾을 수 없습니다. jobPostingId=" + jobPostingId
                ));

        if (!jobPosting.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 공고에 접근할 수 없습니다.");
        }

        return jobPosting;
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

    private record JobPostingAuditValue(
            Long jobPostingId,
            Long companyId,
            String companyName,
            Long detailClassificationId,
            String task,
            String requirement,
            String preferred
    ) {
        private static JobPostingAuditValue from(JobPosting jobPosting) {
            return new JobPostingAuditValue(
                    jobPosting.getId(),
                    jobPosting.getCompany().getId(),
                    jobPosting.getCompany().getName(),
                    jobPosting.getDetailClassification().getId(),
                    jobPosting.getTask(),
                    jobPosting.getRequirement(),
                    jobPosting.getPreferred()
            );
        }
    }
}
