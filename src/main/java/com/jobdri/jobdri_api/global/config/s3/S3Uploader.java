package com.jobdri.jobdri_api.global.config.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Uploader {
    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    // MultipartFile을 전달받아 S3에 업로드
    public String upload(MultipartFile multipartFile, String dirName) throws IOException {
        File uploadFile = convert(multipartFile)
                .orElseThrow(() -> new IllegalArgumentException("MultipartFile -> File 전환 실패"));
        return upload(uploadFile, dirName);
    }

    // S3에 업로드할 때도 고유한 파일 이름을 사용하도록 수정합니다.
    private String upload(File uploadFile, String dirName) {
        // convert 메소드에서 생성한 고유한 이름을 그대로 사용합니다.
        String fileName = dirName + "/" + uploadFile.getName();
        String uploadImageUrl = putS3(uploadFile, fileName);
        removeNewFile(uploadFile);
        return uploadImageUrl;
    }

    // AWS SDK v2에 맞게 PutObjectRequest를 builder 패턴으로 변경
    private String putS3(File uploadFile, String fileName) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .build();

        try {
            s3Client.putObject(putObjectRequest, RequestBody.fromFile(uploadFile));
            return s3Client.utilities().getUrl(builder -> builder.bucket(bucket).key(fileName)).toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    // AWS SDK v2에 맞게 DeleteObjectRequest를 builder 패턴으로 변경
    public void deleteFile(String fileUrl) {
        try {
            // S3 URL에서 파일 이름(키)만 추출
            java.net.URL url = new java.net.URL(fileUrl);
            String fileName = url.getPath().substring(1); // 맨 앞의 '/' 제거

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName) // 예: 'images/abc.jpg'
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
        } catch (Exception e) {
            log.error("S3 파일 삭제에 실패했습니다. URL: {}", fileUrl, e);
        }
    }

    private void removeNewFile(File targetFile) {
        if (targetFile.delete()) {
            log.info("파일이 삭제되었습니다.");
        } else {
            log.info("파일이 삭제되지 못했습니다.");
        }
    }

    // MultipartFile을 File로 변환하고, 파일명에 UUID를 적용
    private Optional<File> convert(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return Optional.empty();
        }

        String originalFilename = file.getOriginalFilename();
        String storedFileName = UUID.randomUUID() + "." + extractExt(originalFilename);
        File convertFile = new File(System.getProperty("java.io.tmpdir"), storedFileName);

        if (convertFile.createNewFile()) {
            try (FileOutputStream fos = new FileOutputStream(convertFile)) {
                fos.write(file.getBytes());
            }
            return Optional.of(convertFile);
        }
        return Optional.empty();
    }

    // 파일의 확장자를 추출하는 헬퍼 메소드
    private String extractExt(String originalFilename) {
        if (originalFilename != null) {
            int pos = originalFilename.lastIndexOf(".");
            if (pos != -1 && pos < originalFilename.length() - 1) {
                return originalFilename.substring(pos + 1);
            }
        }
        return "";
    }

}
