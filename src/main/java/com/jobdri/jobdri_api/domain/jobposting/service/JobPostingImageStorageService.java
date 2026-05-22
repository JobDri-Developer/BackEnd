package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingImageUploadPresignRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingImageUploadPresignResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.config.s3.S3ObjectUrlService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class JobPostingImageStorageService {

    private static final String BASE_DIR = "job-postings";
    private static final Map<String, String> CONTENT_TYPE_TO_EXTENSION = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    private final S3ObjectUrlService s3ObjectUrlService;
    private final long presignPutExpirationMinutes;

    public JobPostingImageStorageService(
            S3ObjectUrlService s3ObjectUrlService,
            @Value("${spring.cloud.aws.s3.presign-put-expiration-minutes:5}") long presignPutExpirationMinutes
    ) {
        this.s3ObjectUrlService = s3ObjectUrlService;
        this.presignPutExpirationMinutes = presignPutExpirationMinutes;
    }

    public JobPostingImageUploadPresignResponse createUploadPresignUrl(
            Long userId,
            JobPostingImageUploadPresignRequest request
    ) {
        String normalizedContentType = normalizeContentType(request.contentType());
        String extension = CONTENT_TYPE_TO_EXTENSION.get(normalizedContentType);

        if (extension == null) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "지원하는 이미지 형식은 png, jpg, jpeg, webp, gif 입니다."
            );
        }

        String objectKey = buildObjectKey(userId, extension);
        String uploadUrl = s3ObjectUrlService.createPresignedPutUrl(
                objectKey,
                normalizedContentType,
                presignPutExpirationMinutes
        );

        return new JobPostingImageUploadPresignResponse(
                objectKey,
                uploadUrl,
                presignPutExpirationMinutes
        );
    }

    public String createReadableImageUrl(Long userId, String objectKey) {
        validateOwnership(userId, objectKey);
        return s3ObjectUrlService.createPresignedGetUrl(objectKey);
    }

    public void validateOwnership(Long userId, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        String expectedPrefix = buildUserPrefix(userId);
        if (!objectKey.startsWith(expectedPrefix)) {
            throw new GeneralException(
                    GeneralErrorCode.FORBIDDEN,
                    "본인이 업로드한 채용 공고 이미지만 사용할 수 있습니다."
            );
        }
    }

    private String buildObjectKey(Long userId, String extension) {
        return buildUserPrefix(userId) + UUID.randomUUID() + "." + extension;
    }

    private String buildUserPrefix(Long userId) {
        return BASE_DIR + "/" + userId + "/";
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    }
}
