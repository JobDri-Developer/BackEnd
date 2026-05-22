package com.jobdri.jobdri_api.domain.jobposting.dto.response;

public record JobPostingImageUploadPresignResponse(
        String objectKey,
        String uploadUrl,
        long expiresInMinutes
) {
}
