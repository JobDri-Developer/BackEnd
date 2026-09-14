package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationChecklistItemRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationDetailUpdateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationEssayRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationMetricRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationDetailResponse;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationChecklistItem;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationEssay;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationMetric;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobApplicationDetailService {
    private final JobApplicationRepository jobApplicationRepository;
    private final DetailClassificationRepository detailClassificationRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public JobApplicationDetailResponse get(User user, Long jobApplicationId) {
        User validatedUser = userService.validateUser(user);
        return JobApplicationDetailResponse.from(getOwnedApplication(validatedUser, jobApplicationId));
    }

    @Transactional
    @AuditLogEvent(action = "JOB_APPLICATION_UPDATE", targetType = "JOB_APPLICATION", targetId = "#arg1")
    public JobApplicationDetailResponse update(
            User user,
            Long jobApplicationId,
            JobApplicationDetailUpdateRequest request
    ) {
        User validatedUser = lockValidatedUser(user);
        JobApplication application = getOwnedApplicationForUpdate(validatedUser, jobApplicationId);
        validateNotStale(application, request.lastKnownUpdatedAt());
        DetailClassification detailClassification = findOptionalClassification(request.detailClassificationId());

        application.updateDetails(
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
                request.currentLabel(),
                request.currentAt(),
                request.memo(),
                request.gpa(),
                request.maxGpa()
        );
        application.replaceChecklistItems(toChecklistItems(application, request.checklistItems()));
        application.replaceMetrics(toMetrics(application, request.metrics()));
        application.replaceEssays(toEssays(application, request.essays()));

        jobApplicationRepository.flush();
        entityManager.clear();
        return JobApplicationDetailResponse.from(getOwnedApplication(validatedUser, jobApplicationId));
    }

    private JobApplication getOwnedApplication(User user, Long jobApplicationId) {
        JobApplication application = jobApplicationRepository.findDetailedById(jobApplicationId)
                .orElseThrow(() -> notFound(jobApplicationId));
        validateOwner(application, user);
        return application;
    }

    private JobApplication getOwnedApplicationForUpdate(User user, Long jobApplicationId) {
        JobApplication application = jobApplicationRepository.findByIdForUpdate(jobApplicationId)
                .orElseThrow(() -> notFound(jobApplicationId));
        validateOwner(application, user);
        return application;
    }

    private void validateOwner(JobApplication application, User user) {
        if (!application.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 지원 카드에 접근할 수 없습니다.");
        }
    }

    private void validateNotStale(JobApplication application, LocalDateTime lastKnownUpdatedAt) {
        if (application.getUpdatedAt() != null && application.getUpdatedAt().isAfter(lastKnownUpdatedAt)) {
            throw new GeneralException(
                    GeneralErrorCode.JOB_APPLICATION_UPDATE_CONFLICT,
                    "지원 카드가 이미 수정되었습니다. 최신 정보를 다시 조회한 뒤 저장해주세요."
            );
        }
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

    private List<String> normalizeSkills(List<String> skills) {
        return safeList(skills).stream().map(String::trim).toList();
    }

    private List<JobApplicationChecklistItem> toChecklistItems(
            JobApplication application,
            List<JobApplicationChecklistItemRequest> requests
    ) {
        List<JobApplicationChecklistItemRequest> items = safeList(requests);
        return IntStream.range(0, items.size())
                .mapToObj(index -> JobApplicationChecklistItem.create(
                        application,
                        items.get(index).content().trim(),
                        items.get(index).completed(),
                        index
                ))
                .toList();
    }

    private List<JobApplicationMetric> toMetrics(
            JobApplication application,
            List<JobApplicationMetricRequest> requests
    ) {
        List<JobApplicationMetricRequest> metrics = safeList(requests);
        return IntStream.range(0, metrics.size())
                .mapToObj(index -> JobApplicationMetric.create(
                        application,
                        metrics.get(index).type(),
                        metrics.get(index).name().trim(),
                        metrics.get(index).value().trim(),
                        index
                ))
                .toList();
    }

    private List<JobApplicationEssay> toEssays(
            JobApplication application,
            List<JobApplicationEssayRequest> requests
    ) {
        List<JobApplicationEssayRequest> essays = safeList(requests);
        return IntStream.range(0, essays.size())
                .mapToObj(index -> JobApplicationEssay.create(
                        application,
                        essays.get(index).question().trim(),
                        essays.get(index).answer(),
                        index
                ))
                .toList();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private User lockValidatedUser(User user) {
        User validatedUser = userService.validateUser(user);
        return userRepository.findByIdForUpdate(validatedUser.getId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }

    private GeneralException notFound(Long jobApplicationId) {
        return new GeneralException(
                GeneralErrorCode.JOB_APPLICATION_NOT_FOUND,
                "해당 지원 카드를 찾을 수 없습니다. jobApplicationId=" + jobApplicationId
        );
    }
}
