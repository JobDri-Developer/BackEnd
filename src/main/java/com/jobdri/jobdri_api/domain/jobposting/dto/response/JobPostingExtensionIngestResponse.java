package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyCreateResponse;

public record JobPostingExtensionIngestResponse(
        String sourceUrl,
        String sourceSite,
        boolean savedToDatabase,
        String message,
        JobPostingIngestResponse ingest,
        MockApplyCreateResponse mockApply
) {
    public static JobPostingExtensionIngestResponse of(
            String sourceUrl,
            String sourceSite,
            JobPostingIngestResponse ingest,
            MockApplyCreateResponse mockApply
    ) {
        return new JobPostingExtensionIngestResponse(
                sourceUrl,
                sourceSite,
                ingest.isSavedToDatabase(),
                ingest.getMessage(),
                ingest,
                mockApply
        );
    }
}
