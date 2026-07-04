package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JobPostingIngestResponse {

    private boolean savedToDatabase;
    private String message;
    private JobPostingExtractResponse extracted;
    private List<JobPostingClassificationCandidateResponse> candidates;
    private JobPostingClassificationResultResponse classification;
    private JobPostingGenerateResponse generated;
    private JobPostingResponse saved;
}
