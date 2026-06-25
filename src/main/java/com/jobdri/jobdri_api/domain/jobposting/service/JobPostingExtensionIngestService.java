package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingExtensionIngestRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtensionIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyCreateResponse;
import com.jobdri.jobdri_api.domain.mockapply.service.MockApplyService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobPostingExtensionIngestService {

    private final JobPostingIngestService jobPostingIngestService;
    private final MockApplyService mockApplyService;

    public JobPostingExtensionIngestResponse ingest(User user, JobPostingExtensionIngestRequest request) {
        JobPostingIngestResponse ingest = jobPostingIngestService.ingestAndCreate(
                user,
                new JobPostingIngestRequest(request.rawText(), null)
        );

        MockApplyCreateResponse mockApply = null;
        if (ingest.isSavedToDatabase() && ingest.getSaved() != null) {
            mockApply = mockApplyService.createMockApplyFromJobPosting(
                    user,
                    ingest.getSaved().getJobPostingId()
            );
        }

        return JobPostingExtensionIngestResponse.of(
                request.sourceUrl(),
                request.sourceSite(),
                ingest,
                mockApply
        );
    }
}
