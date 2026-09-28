package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationIngestRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationResultResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAiService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingClassificationService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingImageStorageService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingIngestInputValidator;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingIngestQualityValidator;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobApplicationIngestService {
    private static final int CANDIDATE_LIMIT = 5;

    @Value("${job-posting.ingest.classification-confidence-threshold:0.65}")
    private double classificationConfidenceThreshold;

    private final JobPostingAiService jobPostingAiService;
    private final JobPostingClassificationService jobPostingClassificationService;
    private final JobPostingImageStorageService jobPostingImageStorageService;
    private final JobApplicationIngestPersistenceService persistenceService;

    @AuditLogEvent(
            action = "JOB_APPLICATION_CLIPPER_INGEST",
            targetType = "JOB_APPLICATION",
            targetId = "#result.jobApplication?.jobApplicationId",
            condition = "#result.savedToDatabase"
    )
    public JobApplicationIngestResponse ingest(User user, JobApplicationIngestRequest request) {
        var existing = persistenceService.findExisting(user, request.idempotencyKey());
        if (existing.isPresent()) {
            return new JobApplicationIngestResponse(
                    true, true, "동일 요청으로 생성된 지원 카드를 반환했습니다.",
                    null, List.of(), null, null, existing.get()
            );
        }

        List<String> imageObjectKeys = jobPostingImageStorageService.normalizeImageObjectKeys(
                request.imageObjectKey(), request.imageObjectKeys()
        );
        JobPostingIngestInputValidator.validate(request.rawText(), imageObjectKeys);

        JobPostingExtractResponse extracted = jobPostingAiService.extractJobPosting(
                user.getId(), request.rawText(),
                imageObjectKeys.isEmpty() ? null : imageObjectKeys.get(0), imageObjectKeys
        );
        JobPostingIngestQualityValidator.validateExtracted(extracted);

        List<JobPostingClassificationCandidateResponse> candidates =
                jobPostingClassificationService.findCandidates(extracted, CANDIDATE_LIMIT);
        if (candidates.isEmpty()) {
            throw new GeneralException(GeneralErrorCode.CLASSIFICATION_NOT_FOUND, "소분류 후보를 찾을 수 없습니다.");
        }

        JobPostingClassificationResultResponse classification =
                jobPostingAiService.classifyDetailClassification(extracted, candidates);
        if (classification.confidence() < classificationConfidenceThreshold) {
            return new JobApplicationIngestResponse(
                    false, false, "소분류 분류 confidence가 낮아 저장을 보류했습니다.",
                    extracted, candidates, classification, null, null
            );
        }

        JobPostingGenerateResponse generated = jobPostingAiService.generateJobPosting(
                new JobPostingGenerateRequest(
                        extracted.companyName(), null, classification.detailClassificationId(), extracted.rawText(), "",
                        extracted.task(), extracted.requirements(), extracted.preferredQualifications(), null,
                        extracted.jobTitle(), extracted.postingName()
                )
        );
        JobPostingIngestQualityValidator.validateGenerated(generated);

        JobApplicationIngestPersistenceService.PersistResult persisted = persistenceService.persist(
                user, request.idempotencyKey(), classification.detailClassificationId(), extracted, generated
        );
        return new JobApplicationIngestResponse(
                true,
                persisted.idempotentReplay(),
                persisted.idempotentReplay() ? "동일 요청으로 생성된 지원 카드를 반환했습니다." : "지원 카드 등록에 성공했습니다.",
                extracted, candidates, classification, generated, persisted.jobApplication()
        );
    }
}
