package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
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
public class JobApplicationMockApplyPersistenceService {
    private static final String DEFAULT_TASK_FORMAT = "%s 업무 수행";
    private static final String DEFAULT_REQUIREMENT_FORMAT = "%s 직무 수행에 필요한 역량";
    private static final String DEFAULT_PREFERRED_FORMAT = "%s 관련 실무 경험";

    private final JobApplicationRepository jobApplicationRepository;
    private final JobPostingService jobPostingService;
    private final MockApplyService mockApplyService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final DetailClassificationRepository detailClassificationRepository;

    /** 잠금 없이 읽은 전환 입력값. 소분류 분류처럼 오래 걸리는 작업을 트랜잭션 밖에서 하기 위해 쓴다. */
    public record ConversionInput(
            boolean linked,
            Long detailClassificationId,
            String companyName,
            String postingName,
            String jobTitle,
            String task,
            String requirement,
            String preferred,
            List<String> requiredSkills
    ) {
    }

    @Transactional(readOnly = true)
    public ConversionInput readInput(User user, Long jobApplicationId) {
        User validatedUser = userService.validateUser(user);
        JobApplication application = jobApplicationRepository.findDetailedById(jobApplicationId)
                .orElseThrow(() -> notFound(jobApplicationId));
        validateOwner(application, validatedUser);
        return new ConversionInput(
                application.getMockApply() != null,
                application.getDetailClassification() == null ? null : application.getDetailClassification().getId(),
                application.getCompanyName(),
                application.getPostingName(),
                application.getJobTitle(),
                taskOrDefault(application.getTask(), application.getJobTitle()),
                requirementOrDefault(application.getRequirement(), application.getJobTitle()),
                preferredOrDefault(application.getPreferred(), application.getJobTitle()),
                List.copyOf(application.getRequiredSkills())
        );
    }

    @Transactional
    @AuditLogEvent(
            action = "JOB_APPLICATION_MOCK_APPLY_CONVERT",
            targetType = "JOB_APPLICATION",
            targetId = "#arg1"
    )
    public JobApplicationMockApplyResponse convert(User user, Long jobApplicationId, Long classifiedDetailClassificationId) {
        User validatedUser = lockValidatedUser(user);
        JobApplication application = jobApplicationRepository.findForConversionByIdForUpdate(jobApplicationId)
                .orElseThrow(() -> notFound(jobApplicationId));
        validateOwner(application, validatedUser);

        if (application.getMockApply() != null) {
            validateLinkedOwnership(application.getMockApply(), validatedUser);
            return JobApplicationMockApplyResponse.of(jobApplicationId, application.getMockApply(), false);
        }

        DetailClassification classification = application.getDetailClassification() != null
                ? application.getDetailClassification()
                : findClassification(classifiedDetailClassificationId);
        validateReady(application, classification);
        JobPosting jobPosting = jobPostingService.createJobPostingEntity(
                validatedUser,
                toJobPostingCreateRequest(application, classification)
        );
        MockApply mockApply = mockApplyService.createActualApply(validatedUser, jobPosting);
        application.assignMockApply(mockApply);
        jobApplicationRepository.flush();
        return JobApplicationMockApplyResponse.of(jobApplicationId, mockApply, true);
    }

    private DetailClassification findClassification(Long detailClassificationId) {
        return detailClassificationId == null
                ? null
                : detailClassificationRepository.findById(detailClassificationId).orElse(null);
    }

    private JobPostingCreateRequest toJobPostingCreateRequest(
            JobApplication application,
            DetailClassification classification
    ) {
        String jobTitle = application.getJobTitle();
        return new JobPostingCreateRequest(
                JobPostingProfileColor.DEFAULT,
                application.getPostingName().trim(),
                application.getCompanyName().trim(),
                application.getCompanySize(),
                application.getJobTitle().trim(),
                classification.getId(),
                taskOrDefault(application.getTask(), jobTitle),
                requirementOrDefault(application.getRequirement(), jobTitle),
                preferredOrDefault(application.getPreferred(), jobTitle)
        );
    }

    // 수동 등록 카드는 JD 상세가 비어 있을 수 있어 직무명으로 기본 문장을 채운다. 카드 자체는 바꾸지 않는다.
    static String taskOrDefault(String task, String jobTitle) {
        return orDefault(task, DEFAULT_TASK_FORMAT, jobTitle);
    }

    static String requirementOrDefault(String requirement, String jobTitle) {
        return orDefault(requirement, DEFAULT_REQUIREMENT_FORMAT, jobTitle);
    }

    static String preferredOrDefault(String preferred, String jobTitle) {
        return orDefault(preferred, DEFAULT_PREFERRED_FORMAT, jobTitle);
    }

    private static String orDefault(String value, String format, String jobTitle) {
        return value == null || value.isBlank() ? format.formatted(jobTitle.trim()) : value.trim();
    }

    private void validateReady(JobApplication application, DetailClassification classification) {
        List<String> missingFields = new ArrayList<>();
        addIfBlank(missingFields, "companyName", application.getCompanyName());
        addIfBlank(missingFields, "postingName", application.getPostingName());
        addIfBlank(missingFields, "jobTitle", application.getJobTitle());
        if (classification == null) {
            missingFields.add("detailClassificationId");
        }

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
