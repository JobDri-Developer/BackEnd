package com.jobdri.jobdri_api.domain.jobposting.dto.worker;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationResultResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;

import java.util.List;

public record JobPostingWorkerFinalizeRequest(
        String taskId,
        Long userId,
        JobPostingExtractResponse extracted,
        List<JobPostingClassificationCandidateResponse> candidates,
        JobPostingClassificationResultResponse classification,
        JobPostingGenerateResponse generated
) {
}
