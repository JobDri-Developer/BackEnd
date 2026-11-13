package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationMockApplyResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationNotReadyResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationResultResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAiService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingClassificationService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class JobApplicationMockApplyService {
    private static final int CANDIDATE_LIMIT = 5;

    @Value("${job-posting.ingest.classification-confidence-threshold:0.65}")
    private double classificationConfidenceThreshold;

    private final JobApplicationMockApplyPersistenceService persistenceService;
    private final JobPostingClassificationService jobPostingClassificationService;
    private final JobPostingAiService jobPostingAiService;

    /**
     * 소분류가 없는 카드는 먼저 분류한 뒤 전환한다. 분류는 LLM을 부르므로 잠금을 잡기 전에 끝낸다.
     */
    public JobApplicationMockApplyResponse createOrGet(User user, Long jobApplicationId) {
        JobApplicationMockApplyPersistenceService.ConversionInput input =
                persistenceService.readInput(user, jobApplicationId);
        Long classifiedId = input.linked() || input.detailClassificationId() != null ? null : classify(input);
        return persistenceService.convert(user, jobApplicationId, classifiedId);
    }

    private Long classify(JobApplicationMockApplyPersistenceService.ConversionInput input) {
        JobPostingExtractResponse extracted = new JobPostingExtractResponse(
                input.postingName(), input.companyName(), input.jobTitle(), input.task(),
                input.requirement(), input.preferred(), rawText(input), 1.0, input.requiredSkills(), ""
        );
        List<JobPostingClassificationCandidateResponse> candidates =
                jobPostingClassificationService.findCandidates(extracted, CANDIDATE_LIMIT);
        if (candidates.isEmpty()) {
            throw classificationRequired(candidates);
        }
        JobPostingClassificationResultResponse classification =
                jobPostingAiService.classifyDetailClassification(extracted, candidates);
        if (classification.detailClassificationId() == null
                || classification.confidence() < classificationConfidenceThreshold) {
            throw classificationRequired(candidates);
        }
        return classification.detailClassificationId();
    }

    private String rawText(JobApplicationMockApplyPersistenceService.ConversionInput input) {
        return Stream.of(input.postingName(), input.jobTitle(), input.task(), input.requirement(), input.preferred(),
                        String.join(", ", input.requiredSkills()))
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private GeneralException classificationRequired(List<JobPostingClassificationCandidateResponse> candidates) {
        return new GeneralException(
                GeneralErrorCode.JOB_APPLICATION_NOT_READY,
                "직무 소분류를 자동으로 정하지 못했습니다. 후보 중 하나를 선택해 저장한 뒤 다시 시도해주세요.",
                new JobApplicationNotReadyResponse(List.of("detailClassificationId"), List.copyOf(candidates))
        );
    }
}
