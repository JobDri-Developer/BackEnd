package com.jobdri.jobdri_api.domain.jobapplication.dto.response;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;

import java.util.List;

/**
 * classificationCandidates는 소분류를 자동으로 정하지 못했을 때 사용자가 고를 수 있는 후보다.
 */
public record JobApplicationNotReadyResponse(
        List<String> missingFields,
        List<JobPostingClassificationCandidateResponse> classificationCandidates
) {
    public JobApplicationNotReadyResponse(List<String> missingFields) {
        this(missingFields, List.of());
    }
}
