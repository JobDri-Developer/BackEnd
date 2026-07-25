package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionAnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingUpdateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplySequenceRepository;
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
    private final MockApplySequenceRepository mockApplySequenceRepository;
    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;
    private final AnalysisRepository analysisRepository;
    private final QuestionAnalysisRepository questionAnalysisRepository;

    @Transactional
    @AuditLogEvent(action = "JOB_POSTING_CREATE", targetType = "JOB_POSTING", targetId = "#result.getJobPostingId()")
    public JobPostingResponse createJobPosting(User user, JobPostingCreateRequest request) {
        User validatedUser = userService.validateUser(user);
        Company company = findOrCreateCompany(request.companyName(), request.companySize());
        DetailClassification detailClassification = findDetailClassification(request.detailClassificationId());

        JobPosting jobPosting = JobPosting.create(
                validatedUser,
                company,
                detailClassification,
                request.profileColor(),
                request.postingName(),
                request.jobTitle(),
                request.task(),
                request.requirement(),
                request.preferred()
        );

        return JobPostingResponse.from(jobPostingRepository.save(jobPosting));
    }

    @Transactional
    @AuditLogEvent(action = "JOB_POSTING_UPDATE", targetType = "JOB_POSTING", targetId = "#arg1")
    public JobPostingResponse updateJobPosting(User user, Long jobPostingId, JobPostingUpdateRequest request) {
        User validatedUser = userService.validateUser(user);
        JobPosting jobPosting = getOwnedJobPosting(validatedUser, jobPostingId);

        Company company = findOrCreateCompany(request.companyName(), request.companySize());
        DetailClassification detailClassification = findDetailClassification(request.detailClassificationId());

        jobPosting.update(
                validatedUser,
                company,
                detailClassification,
                request.profileColor(),
                request.postingName(),
                request.jobTitle(),
                request.task(),
                request.requirement(),
                request.preferred()
        );

        return JobPostingResponse.from(jobPosting);
    }

    public JobPostingResponse getJobPosting(User user, Long jobPostingId) {
        User validatedUser = userService.validateUser(user);
        return JobPostingResponse.from(getOwnedJobPosting(validatedUser, jobPostingId));
    }

    public List<JobPostingResponse> getAllJobPostings(User user) {
        User validatedUser = userService.validateUser(user);
        return jobPostingRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(validatedUser.getId()).stream()
                .map(JobPostingResponse::from)
                .toList();
    }

    public List<JobPostingResponse> getJobPostingsByCompany(User user, Long companyId) {
        User validatedUser = userService.validateUser(user);
        return jobPostingRepository.findAllByUserIdAndCompanyId(validatedUser.getId(), companyId).stream()
                .map(JobPostingResponse::from)
                .toList();
    }

    @Transactional
    @AuditLogEvent(action = "JOB_POSTING_DELETE", targetType = "JOB_POSTING", targetId = "#arg1")
    public void deleteJobPosting(User user, Long jobPostingId) {
        User validatedUser = userService.validateUser(user);
        JobPosting jobPosting = getOwnedJobPosting(validatedUser, jobPostingId);

        questionAnalysisRepository.deleteAllByJobPostingId(jobPostingId);
        questionRepository.deleteAllByJobPostingId(jobPostingId);
        analysisRepository.deleteAllByJobPostingId(jobPostingId);
        mockApplyRepository.deleteAllByJobPostingId(jobPostingId);
        mockApplySequenceRepository.deleteAllByUserIdAndJobPostingId(validatedUser.getId(), jobPostingId);
        jobPostingRepository.delete(jobPosting);
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
}
