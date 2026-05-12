package com.jobdri.jobdri_api.domain.jobposting.repository;

public interface JobPostingClassificationCandidateProjection {

    Long getDetailClassificationId();

    String getDetailClassificationName();

    String getMiddleClassificationName();

    String getBigClassificationName();

    Double getScore();
}
