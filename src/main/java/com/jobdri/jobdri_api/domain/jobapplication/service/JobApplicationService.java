package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationCreateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationFromJobPostingRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationResponse;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
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
public class JobApplicationService {
    private final JobApplicationRepository jobApplicationRepository;
    private final DetailClassificationRepository detailClassificationRepository;
    private final JobPostingService jobPostingService;
    private final UserService userService;
    private final UserRepository userRepository;

    @Transactional
    @AuditLogEvent(action = "JOB_APPLICATION_CREATE", targetType = "JOB_APPLICATION", targetId = "#result.jobApplicationId")
    public JobApplicationResponse create(User user, JobApplicationCreateRequest request) {
        User validatedUser = lockValidatedUser(user);
        JobApplicationStage stage = request.initialStage() == null
                ? JobApplicationStage.PLANNED
                : request.initialStage();
        DetailClassification detailClassification = findOptionalClassification(request.detailClassificationId());

        JobApplication application = JobApplication.create(
                validatedUser,
                null,
                detailClassification,
                request.companyName().trim(),
                request.postingName().trim(),
                request.jobTitle().trim(),
                request.companySize(),
                request.task(),
                request.requirement(),
                request.preferred(),
                normalizeSkills(request.requiredSkills()),
                request.deadlineAt(),
                stage,
                nextStageOrder(validatedUser.getId(), stage),
                request.currentLabel(),
                request.currentAt()
        );
        return JobApplicationResponse.from(jobApplicationRepository.save(application));
    }

    @Transactional
    @AuditLogEvent(action = "JOB_APPLICATION_CREATE_FROM_JOB_POSTING", targetType = "JOB_APPLICATION", targetId = "#result.jobApplicationId")
    public JobApplicationResponse createFromJobPosting(User user, JobApplicationFromJobPostingRequest request) {
        User validatedUser = lockValidatedUser(user);
        JobPosting source = jobPostingService.getOwnedJobPostingForUpdate(validatedUser, request.jobPostingId());
        JobApplicationStage stage = request.initialStage() == null
                ? JobApplicationStage.PLANNED
                : request.initialStage();

        JobApplication application = JobApplication.create(
                validatedUser,
                source,
                source.getDetailClassification(),
                source.getCompany().getName(),
                source.getPostingName(),
                source.getJobTitle(),
                source.getCompany().getSize(),
                source.getTask(),
                source.getRequirement(),
                source.getPreferred(),
                List.of(),
                null,
                stage,
                nextStageOrder(validatedUser.getId(), stage),
                null,
                null
        );
        return JobApplicationResponse.from(jobApplicationRepository.save(application));
    }

    public JobApplicationResponse get(User user, Long jobApplicationId) {
        User validatedUser = userService.validateUser(user);
        return JobApplicationResponse.from(getOwnedApplication(validatedUser, jobApplicationId));
    }

    public JobApplication getOwnedApplication(User user, Long jobApplicationId) {
        JobApplication application = jobApplicationRepository.findDetailedById(jobApplicationId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_APPLICATION_NOT_FOUND,
                        "해당 지원 카드를 찾을 수 없습니다. jobApplicationId=" + jobApplicationId
                ));
        if (!application.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 지원 카드에 접근할 수 없습니다.");
        }
        return application;
    }

    private DetailClassification findOptionalClassification(Long detailClassificationId) {
        if (detailClassificationId == null) {
            return null;
        }
        return detailClassificationRepository.findById(detailClassificationId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.CLASSIFICATION_NOT_FOUND,
                        "해당 소분류를 찾을 수 없습니다. detailClassificationId=" + detailClassificationId
                ));
    }

    private int nextStageOrder(Long userId, JobApplicationStage stage) {
        return jobApplicationRepository.findMaxStageOrder(userId, stage) + 1;
    }

    private List<String> normalizeSkills(List<String> skills) {
        if (skills == null) {
            return List.of();
        }
        return skills.stream().map(String::trim).toList();
    }

    private User lockValidatedUser(User user) {
        User validatedUser = userService.validateUser(user);
        return userRepository.findByIdForUpdate(validatedUser.getId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }
}
