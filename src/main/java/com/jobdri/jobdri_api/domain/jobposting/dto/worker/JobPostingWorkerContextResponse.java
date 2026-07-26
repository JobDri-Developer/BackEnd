package com.jobdri.jobdri_api.domain.jobposting.dto.worker;

import java.util.List;

public record JobPostingWorkerContextResponse(
        String imageUrl,
        List<String> imageUrls
) {
    public JobPostingWorkerContextResponse(String imageUrl) {
        this(imageUrl, imageUrl == null ? List.of() : List.of(imageUrl));
    }
}
