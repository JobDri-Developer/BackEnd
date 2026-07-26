package com.jobdri.jobdri_api.domain.mockapply.dto.response;

import com.jobdri.jobdri_api.domain.analysis.entity.Analysis;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;

import java.time.LocalDateTime;

public record MockApplyHomeItemResponse(
        Long mockApplyId,
        String resumePath,
        Long jobPostingId,
        String displayName,
        int sequence,
        MockApplyStatus status,
        String companyName,
        String detailClassificationName,
        String jobTitle,
        LocalDateTime createdAt,
        ApplyType applyType,
        Integer score
) {
    public static MockApplyHomeItemResponse from(MockApply mockApply) {
        JobPosting jobPosting = mockApply.getJobPosting();
        String detailClassificationName = jobPosting.getDetailClassification().getDetailName();
        Analysis analysis = mockApply.getAnalysis();

        return new MockApplyHomeItemResponse(
                mockApply.getId(),
                resumePath(mockApply),
                jobPosting.getId(),
                mockApply.getDisplayName(),
                mockApply.getSequence() == null ? 1 : mockApply.getSequence(),
                mockApply.getStatus(),
                jobPosting.getCompany().getName(),
                detailClassificationName,
                jobPosting.getJobTitle(),
                mockApply.getCreatedAt(),
                mockApply.getApplyType(),
                analysis == null ? null : analysis.getScore()
        );
    }

    private static String resumePath(MockApply mockApply) {
        return switch (mockApply.getStatus()) {
            case APPLICATION_CREATED -> "/mock-applies/" + mockApply.getId() + "/job-posting";
            case QUESTION_SELECT -> "/mock-applies/" + mockApply.getId() + "/questions";
            case ANSWER_WRITE -> "/mock-applies/" + mockApply.getId() + "/answers";
            case COMPLETED -> "/mock-applies/" + mockApply.getId() + "/analysis";
        };
    }
}
