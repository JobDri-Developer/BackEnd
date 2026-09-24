package com.jobdri.jobdri_api.domain.jobapplication.dto.response;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationResultResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;

import java.util.List;

public record JobApplicationIngestResponse(
        boolean savedToDatabase,
        boolean idempotentReplay,
        String message,
        JobPostingExtractResponse extracted,
        List<JobPostingClassificationCandidateResponse> candidates,
        JobPostingClassificationResultResponse classification,
        JobPostingGenerateResponse generated,
        JobApplicationResponse jobApplication
) {
}
