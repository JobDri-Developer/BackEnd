package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestCommand;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestMultipartRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationResultResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobPostingIngestService {

    private static final int DEFAULT_CANDIDATE_LIMIT = 10;

    @Value("${job-posting.ingest.classification-confidence-threshold}")
    private double classificationConfidenceThreshold;

    private final JobPostingAiService jobPostingAiService;
    private final JobPostingClassificationService jobPostingClassificationService;
    private final JobPostingService jobPostingService;

    public JobPostingIngestResponse ingestAndCreate(JobPostingIngestMultipartRequest request) {
        JobPostingIngestCommand command = JobPostingIngestCommand.builder()
                .rawText(request.rawText())
                .sourceUrl(request.sourceUrl())
                .companySize(request.companySize())
                .candidateLimit(request.candidateLimit())
                .build();
        return ingestAndCreate(command);
    }

    public JobPostingIngestResponse ingestAndCreate(JobPostingIngestCommand command) {

        JobPostingExtractResponse extracted = jobPostingAiService.extractJobPosting(
                command.getRawText(),
                command.getImageBytes(),
                command.getImageContentType(),
                command.getSourceUrl()
        );

        int candidateLimit = command.getCandidateLimit() == null ? DEFAULT_CANDIDATE_LIMIT : command.getCandidateLimit();
        List<JobPostingClassificationCandidateResponse> candidates =
                jobPostingClassificationService.findCandidates(extracted, candidateLimit);

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
                        command.getCompanySize(),
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
                new JobPostingCreateRequest(
                        fallbackCompanyName(extracted.companyName()),
                        command.getCompanySize(),
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
}
