package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationResponse;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JobApplicationIngestPersistenceService {
    private final JobApplicationRepository jobApplicationRepository;
    private final DetailClassificationRepository detailClassificationRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<JobApplicationResponse> findExisting(User user, String idempotencyKey) {
        User validatedUser = userService.validateUser(user);
        return jobApplicationRepository.findByUserIdAndIngestIdempotencyKey(
                        validatedUser.getId(), idempotencyKey.trim()
                )
                .map(JobApplicationResponse::from);
    }

    @Transactional
    public PersistResult persist(
            User user,
            String idempotencyKey,
            Long detailClassificationId,
            JobPostingExtractResponse extracted,
            JobPostingGenerateResponse generated
    ) {
        User validatedUser = lockValidatedUser(user);
        String normalizedKey = idempotencyKey.trim();
        return jobApplicationRepository.findByUserIdAndIngestIdempotencyKey(validatedUser.getId(), normalizedKey)
                .map(existing -> new PersistResult(JobApplicationResponse.from(existing), true))
                .orElseGet(() -> create(
                        validatedUser, normalizedKey, detailClassificationId, extracted, generated
                ));
    }

    private PersistResult create(
            User user,
            String idempotencyKey,
            Long detailClassificationId,
            JobPostingExtractResponse extracted,
            JobPostingGenerateResponse generated
    ) {
        DetailClassification classification = detailClassificationRepository.findById(detailClassificationId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.CLASSIFICATION_NOT_FOUND,
                        "해당 소분류를 찾을 수 없습니다. detailClassificationId=" + detailClassificationId
                ));
        int stageOrder = jobApplicationRepository.findMaxStageOrder(user.getId(), JobApplicationStage.PLANNED) + 1;
        JobApplication application = JobApplication.createFromIngest(
                user,
                classification,
                generated.companyName(),
                generated.postingName(),
                generated.jobTitle(),
                generated.task(),
                generated.requirements(),
                generated.preferredQualifications(),
                normalizeSkills(extracted.requiredSkills()),
                parseDeadline(extracted.deadlineAt()),
                stageOrder,
                idempotencyKey
        );
        return new PersistResult(JobApplicationResponse.from(jobApplicationRepository.save(application)), false);
    }

    private User lockValidatedUser(User user) {
        User validatedUser = userService.validateUser(user);
        return userRepository.findByIdForUpdate(validatedUser.getId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }

    private List<String> normalizeSkills(List<String> skills) {
        if (skills == null) {
            return List.of();
        }
        return skills.stream()
                .filter(skill -> skill != null && !skill.isBlank())
                .map(String::trim)
                .filter(skill -> skill.length() <= 50)
                .distinct()
                .limit(20)
                .toList();
    }

    private LocalDateTime parseDeadline(String deadlineAt) {
        if (deadlineAt == null || deadlineAt.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(deadlineAt.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public record PersistResult(JobApplicationResponse jobApplication, boolean idempotentReplay) {
    }
}
