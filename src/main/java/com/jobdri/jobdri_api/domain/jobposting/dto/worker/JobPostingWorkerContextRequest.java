package com.jobdri.jobdri_api.domain.jobposting.dto.worker;

public record JobPostingWorkerContextRequest(
        Long userId,
        String imageObjectKey
) {
}
