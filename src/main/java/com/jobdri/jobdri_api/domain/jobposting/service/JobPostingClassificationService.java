package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingClassificationCandidateProjection;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingClassificationService {

    private final DetailClassificationRepository detailClassificationRepository;

    public List<JobPostingClassificationCandidateResponse> findCandidates(JobPostingExtractResponse extracted, int limit) {
        String query = buildSearchQuery(extracted);

        return detailClassificationRepository.findTopCandidatesByTrigram(query, limit).stream()
                .map(this::toResponse)
                .toList();
    }

    private String buildSearchQuery(JobPostingExtractResponse extracted) {
        return String.join(" ",
                normalize(extracted.getJobTitle()),
                normalize(extracted.getTask()),
                normalize(extracted.getRequirements()),
                normalize(extracted.getPreferredQualifications()),
                normalize(extracted.getRawText())
        ).trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }

    private JobPostingClassificationCandidateResponse toResponse(JobPostingClassificationCandidateProjection projection) {
        return new JobPostingClassificationCandidateResponse(
                projection.getDetailClassificationId(),
                projection.getDetailClassificationName(),
                projection.getMiddleClassificationName(),
                projection.getBigClassificationName(),
                projection.getScore() == null ? 0.0 : projection.getScore()
        );
    }
}
