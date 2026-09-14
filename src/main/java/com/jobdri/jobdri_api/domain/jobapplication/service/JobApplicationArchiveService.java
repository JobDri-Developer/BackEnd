package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationArchiveItemResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationResponse;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.pagination.PaginationPolicy;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobApplicationArchiveService {
    public static final int MAX_PAGE_SIZE = PaginationPolicy.MAX_PAGE_SIZE;

    private final JobApplicationRepository jobApplicationRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Transactional
    @AuditLogEvent(action = "JOB_APPLICATION_ARCHIVE", targetType = "JOB_APPLICATION", targetId = "#arg1")
    public JobApplicationResponse archive(User user, Long jobApplicationId) {
        User validatedUser = lockValidatedUser(user);
        JobApplication application = getOwnedApplicationForUpdate(validatedUser, jobApplicationId);
        if (application.getArchivedAt() != null) {
            throw conflict("이미 보관된 지원 카드입니다.");
        }

        List<JobApplication> column = jobApplicationRepository.lockActiveColumns(
                validatedUser.getId(), List.of(application.getStage()));
        application.archive(LocalDateTime.now());
        reorder(column.stream().filter(card -> !card.getId().equals(jobApplicationId)).toList(), application.getStage());
        return flushAndReload(application.getId());
    }

    @Transactional
    @AuditLogEvent(action = "JOB_APPLICATION_RESTORE", targetType = "JOB_APPLICATION", targetId = "#arg1")
    public JobApplicationResponse restore(User user, Long jobApplicationId) {
        User validatedUser = lockValidatedUser(user);
        JobApplication application = getOwnedApplicationForUpdate(validatedUser, jobApplicationId);
        if (application.getArchivedAt() == null) {
            throw conflict("보관되지 않은 지원 카드는 복원할 수 없습니다.");
        }

        int lastOrder = jobApplicationRepository.findMaxStageOrder(validatedUser.getId(), application.getStage()) + 1;
        application.restore(lastOrder);
        return flushAndReload(application.getId());
    }

    public Page<JobApplicationArchiveItemResponse> getArchive(User user, int page, int size) {
        User validatedUser = userService.validateUser(user);
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("archivedAt"), Sort.Order.desc("id"))
        );
        return jobApplicationRepository.findArchivePageByUserId(validatedUser.getId(), pageable);
    }

    @Transactional
    @AuditLogEvent(action = "JOB_APPLICATION_DELETE", targetType = "JOB_APPLICATION", targetId = "#arg1")
    public void delete(User user, Long jobApplicationId) {
        User validatedUser = lockValidatedUser(user);
        JobApplication application = getOwnedApplicationForUpdate(validatedUser, jobApplicationId);
        if (application.getArchivedAt() == null) {
            List<JobApplication> column = jobApplicationRepository.lockActiveColumns(
                    validatedUser.getId(), List.of(application.getStage()));
            reorder(column.stream().filter(card -> !card.getId().equals(jobApplicationId)).toList(), application.getStage());
        }
        jobApplicationRepository.delete(application);
        jobApplicationRepository.flush();
    }

    private JobApplicationResponse flushAndReload(Long jobApplicationId) {
        jobApplicationRepository.flush();
        entityManager.clear();
        JobApplication application = jobApplicationRepository.findDetailedById(jobApplicationId)
                .orElseThrow(() -> notFound(jobApplicationId));
        return JobApplicationResponse.from(application);
    }

    private void reorder(List<JobApplication> cards, JobApplicationStage stage) {
        for (int index = 0; index < cards.size(); index++) {
            cards.get(index).moveTo(stage, index);
        }
    }

    private JobApplication getOwnedApplicationForUpdate(User user, Long jobApplicationId) {
        JobApplication application = jobApplicationRepository.findByIdForUpdate(jobApplicationId)
                .orElseThrow(() -> notFound(jobApplicationId));
        if (!application.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 지원 카드에 접근할 수 없습니다.");
        }
        return application;
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

    private GeneralException conflict(String message) {
        return new GeneralException(GeneralErrorCode.JOB_APPLICATION_UPDATE_CONFLICT, message);
    }
}
