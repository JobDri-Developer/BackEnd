package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingGenerateRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobPostingIngestService {

    private static final int DEFAULT_CANDIDATE_LIMIT = 10;

    private final JobPostingAiService jobPostingAiService;
    private final JobPostingClassificationService jobPostingClassificationService;
    private final JobPostingService jobPostingService;

    @Transactional
    public JobPostingIngestResponse ingestAndCreate(JobPostingIngestMultipartRequest request) {
        if (request.getCompanySize() == null) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "회사 규모는 필수입니다.");
        }

        JobPostingExtractResponse extracted = jobPostingAiService.extractJobPosting(
                request.getRawText(),
                request.getImage(),
                request.getSourceUrl()
        );

        int candidateLimit = request.getCandidateLimit() == null ? DEFAULT_CANDIDATE_LIMIT : request.getCandidateLimit();
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

        JobPostingGenerateResponse generated = jobPostingAiService.generateJobPosting(
                new JobPostingGenerateRequest(
                        extracted.getCompanyName(),
                        request.getCompanySize(),
                        classification.getDetailClassificationId(),
                        extracted.getRawText(),
                        "",
                        extracted.getTask(),
                        extracted.getRequirements(),
                        extracted.getPreferredQualifications(),
                        request.getTone(),
                        extracted.getJobTitle()
                )
        );

        JobPostingResponse saved = jobPostingService.createJobPosting(
                new JobPostingCreateRequest(
                        fallbackCompanyName(extracted.getCompanyName()),
                        request.getCompanySize(),
                        classification.getDetailClassificationId(),
                        generated.getTask(),
                        generated.getRequirements(),
                        generated.getPreferredQualifications()
                )
        );

        return new JobPostingIngestResponse(extracted, candidates, classification, generated, saved);
    }

    private String fallbackCompanyName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return "미분류 회사";
        }
        return companyName;
    }
}
