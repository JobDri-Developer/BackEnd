package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationMockApplyResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationNotReadyResponse;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingProfileColor;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingService;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.service.MockApplyService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobApplicationMockApplyService {
    private final JobApplicationRepository jobApplicationRepository;
    private final JobPostingService jobPostingService;
    private final MockApplyService mockApplyService;
    private final UserService userService;
    private final UserRepository userRepository;

    @Transactional
    @AuditLogEvent(
            action = "JOB_APPLICATION_MOCK_APPLY_CONVERT",
            targetType = "JOB_APPLICATION",
            targetId = "#arg1"
    )
    public JobApplicationMockApplyResponse createOrGet(User user, Long jobApplicationId) {
        User validatedUser = lockValidatedUser(user);
        JobApplication application = jobApplicationRepository.findForConversionByIdForUpdate(jobApplicationId)
                .orElseThrow(() -> notFound(jobApplicationId));
        validateOwner(application, validatedUser);

        if (application.getMockApply() != null) {
            validateLinkedOwnership(application.getMockApply(), validatedUser);
            return JobApplicationMockApplyResponse.of(jobApplicationId, application.getMockApply(), false);
        }

        validateReady(application);
        JobPosting jobPosting = jobPostingService.createJobPostingEntity(
                validatedUser,
                toJobPostingCreateRequest(application)
        );
        MockApply mockApply = mockApplyService.createActualApply(validatedUser, jobPosting);
        application.assignMockApply(mockApply);
        jobApplicationRepository.flush();
        return JobApplicationMockApplyResponse.of(jobApplicationId, mockApply, true);
    }

    private JobPostingCreateRequest toJobPostingCreateRequest(JobApplication application) {
        return new JobPostingCreateRequest(
                JobPostingProfileColor.DEFAULT,
                application.getPostingName().trim(),
                application.getCompanyName().trim(),
                application.getCompanySize(),
                application.getJobTitle().trim(),
                application.getDetailClassification().getId(),
                application.getTask().trim(),
                application.getRequirement().trim(),
                application.getPreferred().trim()
        );
    }

    private void validateReady(JobApplication application) {
        List<String> missingFields = new ArrayList<>();
        addIfBlank(missingFields, "companyName", application.getCompanyName());
        addIfBlank(missingFields, "postingName", application.getPostingName());
        addIfBlank(missingFields, "jobTitle", application.getJobTitle());
        if (application.getDetailClassification() == null) {
            missingFields.add("detailClassificationId");
        }
        addIfBlank(missingFields, "task", application.getTask());
        addIfBlank(missingFields, "requirement", application.getRequirement());
        addIfBlank(missingFields, "preferred", application.getPreferred());

        if (!missingFields.isEmpty()) {
            throw new GeneralException(
                    GeneralErrorCode.JOB_APPLICATION_NOT_READY,
                    "모의지원 전환에 필요한 정보가 부족합니다.",
                    new JobApplicationNotReadyResponse(List.copyOf(missingFields))
            );
        }
    }

    private void addIfBlank(List<String> missingFields, String field, String value) {
        if (value == null || value.isBlank()) {
            missingFields.add(field);
        }
    }

    private User lockValidatedUser(User user) {
        User validatedUser = userService.validateUser(user);
        return userRepository.findByIdForUpdate(validatedUser.getId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }

    private void validateOwner(JobApplication application, User user) {
        if (!application.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 지원 카드에 접근할 수 없습니다.");
        }
    }

    private void validateLinkedOwnership(MockApply mockApply, User user) {
        if (!mockApply.getUser().getId().equals(user.getId())
                || !mockApply.getJobPosting().getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "연결된 모의지원에 접근할 수 없습니다.");
        }
    }

    private GeneralException notFound(Long jobApplicationId) {
        return new GeneralException(
                GeneralErrorCode.JOB_APPLICATION_NOT_FOUND,
                "해당 지원 카드를 찾을 수 없습니다. jobApplicationId=" + jobApplicationId
        );
    }
}
