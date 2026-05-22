package com.jobdri.jobdri_api.global.config.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class S3ObjectUrlService {

    private final S3Presigner s3Presigner;

    @Value("${spring.cloud.aws.s3.bucket:dummy-bucket}")
    private String bucket;

    @Value("${spring.cloud.aws.s3.presign-get-expiration-minutes:10}")
    private long presignGetExpirationMinutes;

    public String createPresignedGetUrl(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignGetExpirationMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public String createPresignedPutUrl(String objectKey, String contentType, long expiresInMinutes) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expiresInMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    public String getBucket() {
        return bucket;
    }
}
