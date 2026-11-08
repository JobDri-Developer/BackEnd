package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import java.util.List;

/** 공고 추출 결과를 Java API와 Python worker 사이에서 전달하는 응답 계약. */
public record JobPostingExtractResponse(String postingName, String companyName, String jobTitle, String task,
                                        String requirements, String preferredQualifications, String rawText,
                                        double confidence, List<String> requiredSkills, String deadlineAt) {

    /** 구버전 또는 명시적 null 응답의 기술 목록과 마감일을 빈 기본값으로 정규화한다. */
    public JobPostingExtractResponse {
        requiredSkills = requiredSkills == null ? List.of() : requiredSkills;
        deadlineAt = deadlineAt == null ? "" : deadlineAt;
    }

    /** 기술 목록과 마감일을 전달하지 않는 기존 호출자를 위한 호환 생성자. */
    public JobPostingExtractResponse(
            String postingName,
            String companyName,
            String jobTitle,
            String task,
            String requirements,
            String preferredQualifications,
            String rawText,
            double confidence
    ) {
        this(postingName, companyName, jobTitle, task, requirements, preferredQualifications, rawText, confidence,
                List.of(), "");
    }

    /** 공고명도 전달하지 않는 기존 호출에 빈 공고명·기술 목록·마감일을 제공한다. */
    public JobPostingExtractResponse(
            String companyName,
            String jobTitle,
            String task,
            String requirements,
            String preferredQualifications,
            String rawText,
            double confidence
    ) {
        this("", companyName, jobTitle, task, requirements, preferredQualifications, rawText, confidence,
                List.of(), "");
    }
}
