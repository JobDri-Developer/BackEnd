package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingExtensionIngestRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtensionIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobPostingExtensionIngestService {

    private final JobPostingIngestService jobPostingIngestService;

    public JobPostingExtensionIngestResponse ingest(User user, JobPostingExtensionIngestRequest request) {
        JobPostingIngestResponse ingest = jobPostingIngestService.ingestAndCreate(
                user,
                new JobPostingIngestRequest(request.rawText(), null)
        );

        return JobPostingExtensionIngestResponse.of(
                request.sourceUrl(),
                request.sourceSite(),
                ingest,
                null
        );
    }
}
