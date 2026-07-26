package com.jobdri.jobdri_api.domain.jobposting.dto.worker;

import java.util.List;

public record JobPostingWorkerContextRequest(
        Long userId,
        String imageObjectKey,
        List<String> imageObjectKeys
) {
    public JobPostingWorkerContextRequest(Long userId, String imageObjectKey) {
        this(userId, imageObjectKey, null);
    }
}
