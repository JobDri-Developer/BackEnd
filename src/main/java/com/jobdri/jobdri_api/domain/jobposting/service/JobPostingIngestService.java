package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestCommand;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationResultResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobPostingIngestService {

    private static final int FIXED_CANDIDATE_LIMIT = 5;

    @Value("${job-posting.ingest.classification-confidence-threshold:0.65}")
    private double classificationConfidenceThreshold;

    private final JobPostingAiService jobPostingAiService;
    private final JobPostingClassificationService jobPostingClassificationService;
    private final JobPostingService jobPostingService;
    private final UserService userService;

    public JobPostingIngestResponse ingestAndCreate(User user, JobPostingIngestRequest request) {
        JobPostingIngestCommand command = JobPostingIngestCommand.builder()
                .userId(user.getId())
                .rawText(request.rawText())
                .imageObjectKey(request.imageObjectKey())
                .build();
        return ingestAndCreate(command);
    }

    public JobPostingIngestResponse ingestAndCreate(JobPostingIngestCommand command) {

        JobPostingExtractResponse extracted = jobPostingAiService.extractJobPosting(
                command.getUserId(),
                command.getRawText(),
                command.getImageObjectKey()
        );

        List<JobPostingClassificationCandidateResponse> candidates =
                jobPostingClassificationService.findCandidates(extracted, FIXED_CANDIDATE_LIMIT);

        if (candidates.isEmpty()) {
            throw new GeneralException(
                    GeneralErrorCode.CLASSIFICATION_NOT_FOUND,
                    "소분류 후보를 찾을 수 없습니다."
            );
        }

        JobPostingClassificationResultResponse classification =
                jobPostingAiService.classifyDetailClassification(extracted, candidates);

        if (classification.confidence() < classificationConfidenceThreshold) {
            return new JobPostingIngestResponse(
                    false,
                    "소분류 분류 confidence가 낮아 저장을 보류했습니다.",
                    extracted,
                    candidates,
                    classification,
                    null,
                    null
            );
        }

        JobPostingGenerateResponse generated = jobPostingAiService.generateJobPosting(
                new JobPostingGenerateRequest(
                        extracted.companyName(),
                        null,
                        classification.detailClassificationId(),
                        extracted.rawText(),
                        "",
                        extracted.task(),
                        extracted.requirements(),
                        extracted.preferredQualifications(),
                        null,
                        extracted.jobTitle()
                )
        );

        JobPostingResponse saved = jobPostingService.createJobPosting(
                resolveUser(command),
                new JobPostingCreateRequest(
                        fallbackCompanyName(extracted.companyName()),
                        null,
                        classification.detailClassificationId(),
                        generated.task(),
                        generated.requirements(),
                        generated.preferredQualifications()
                )
        );

        return new JobPostingIngestResponse(
                true,
                "채용 공고 추출 및 저장에 성공했습니다.",
                extracted,
                candidates,
                classification,
                generated,
                saved
        );
    }

    private String fallbackCompanyName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return "미분류 회사";
        }
        return companyName;
    }

    private User resolveUser(JobPostingIngestCommand command) {
        if (command.getUserId() == null) {
            throw new GeneralException(GeneralErrorCode.MISSING_AUTH_INFO, "인증 정보가 누락되었습니다.");
        }
        return userService.getUser(command.getUserId());
    }

}
