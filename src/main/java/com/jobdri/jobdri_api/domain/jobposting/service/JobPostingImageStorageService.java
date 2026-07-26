package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingImageUploadPresignRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingImageUploadPresignResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.config.s3.S3ObjectUrlService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class JobPostingImageStorageService {

    private static final String BASE_DIR = "job-postings/tmp";
    private static final int MAX_IMAGE_COUNT = 2;
    private static final Map<String, String> CONTENT_TYPE_TO_EXTENSION = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg"
    );

    private final S3ObjectUrlService s3ObjectUrlService;
    private final S3Client s3Client;
    private final long presignPutExpirationMinutes;
    private final long maxImageSizeBytes;

    public JobPostingImageStorageService(
            S3ObjectUrlService s3ObjectUrlService,
            S3Client s3Client,
            @Value("${spring.cloud.aws.s3.presign-put-expiration-minutes:5}") long presignPutExpirationMinutes,
            @Value("${job-posting.image-upload.max-size-bytes:5242880}") long maxImageSizeBytes
    ) {
        this.s3ObjectUrlService = s3ObjectUrlService;
        this.s3Client = s3Client;
        this.presignPutExpirationMinutes = presignPutExpirationMinutes;
        this.maxImageSizeBytes = maxImageSizeBytes;
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
                    "지원하는 이미지 형식은 png, jpg, jpeg 입니다."
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
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        validateOwnership(userId, objectKey);
        validateUploadedObject(objectKey);
        return s3ObjectUrlService.createPresignedGetUrl(objectKey);
    }

    public List<String> createReadableImageUrls(Long userId, List<String> objectKeys) {
        List<String> normalizedObjectKeys = normalizeImageObjectKeys(null, objectKeys);
        List<String> imageUrls = new ArrayList<>();
        for (String objectKey : normalizedObjectKeys) {
            imageUrls.add(createReadableImageUrl(userId, objectKey));
        }
        return imageUrls;
    }

    public List<String> normalizeImageObjectKeys(String imageObjectKey, List<String> imageObjectKeys) {
        Set<String> normalized = new LinkedHashSet<>();
        if (imageObjectKey != null && !imageObjectKey.isBlank()) {
            normalized.add(imageObjectKey.trim());
        }
        if (imageObjectKeys != null) {
            for (String objectKey : imageObjectKeys) {
                if (objectKey == null || objectKey.isBlank()) {
                    continue;
                }
                String trimmed = objectKey.trim();
                if (!normalized.add(trimmed)) {
                    throw new GeneralException(
                            GeneralErrorCode.INVALID_PARAMETER,
                            "동일한 이미지 objectKey를 중복으로 전달할 수 없습니다."
                    );
                }
            }
        }
        if (normalized.size() > MAX_IMAGE_COUNT) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이미지는 최대 2개까지 첨부할 수 있습니다."
            );
        }
        return List.copyOf(normalized);
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

    private void validateUploadedObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        HeadObjectResponse headObject;
        try {
            headObject = s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(s3ObjectUrlService.getBucket())
                            .key(objectKey)
                            .build()
            );
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "업로드된 이미지를 찾을 수 없습니다. objectKey=" + objectKey
                );
            }
            if (e.statusCode() == 403) {
                throw new GeneralException(
                        GeneralErrorCode.FORBIDDEN,
                        "업로드된 이미지에 접근할 수 없습니다. objectKey=" + objectKey
                );
            }
            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR,
                    "업로드된 이미지 검증 중 오류가 발생했습니다. objectKey=" + objectKey
            );
        }

        String normalizedContentType = normalizeContentType(headObject.contentType());
        if (!CONTENT_TYPE_TO_EXTENSION.containsKey(normalizedContentType)) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "지원하는 이미지 형식은 png, jpg, jpeg 입니다."
            );
        }

        String lowerObjectKey = objectKey.toLowerCase(Locale.ROOT);
        if (!lowerObjectKey.endsWith(".png")
                && !lowerObjectKey.endsWith(".jpg")
                && !lowerObjectKey.endsWith(".jpeg")) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "지원하는 이미지 확장자는 png, jpg, jpeg 입니다."
            );
        }

        Long contentLength = headObject.contentLength();
        if (contentLength != null && contentLength <= 0) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "빈 이미지 파일은 사용할 수 없습니다."
            );
        }
        if (contentLength != null && contentLength > maxImageSizeBytes) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이미지 파일 크기가 허용 범위를 초과했습니다."
            );
        }
    }
}
