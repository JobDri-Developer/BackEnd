package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.config.s3.S3ObjectUrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class JobPostingImageStorageServiceTest {

    @Mock
    private S3ObjectUrlService s3ObjectUrlService;

    @Mock
    private S3Client s3Client;

    @Test
    @DisplayName("구버전 단일 이미지와 신규 이미지 목록을 순서대로 병합한다")
    void normalizeImageObjectKeysMergesLegacyAndList() {
        JobPostingImageStorageService service = createService();

        List<String> result = service.normalizeImageObjectKeys(
                " job-postings/tmp/1/first.png ",
                List.of("job-postings/tmp/1/second.jpg")
        );

        assertThat(result).containsExactly(
                "job-postings/tmp/1/first.png",
                "job-postings/tmp/1/second.jpg"
        );
    }

    @Test
    @DisplayName("이미지는 최대 2개까지만 허용한다")
    void normalizeImageObjectKeysRejectsMoreThanTwoImages() {
        JobPostingImageStorageService service = createService();

        assertThatThrownBy(() -> service.normalizeImageObjectKeys(
                "job-postings/tmp/1/first.png",
                List.of("job-postings/tmp/1/second.jpg", "job-postings/tmp/1/third.png")
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("동일 objectKey 중복 전달은 차단한다")
    void normalizeImageObjectKeysRejectsDuplicateObjectKeys() {
        JobPostingImageStorageService service = createService();

        assertThatThrownBy(() -> service.normalizeImageObjectKeys(
                "job-postings/tmp/1/first.png",
                List.of(" job-postings/tmp/1/first.png ")
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    private JobPostingImageStorageService createService() {
        return new JobPostingImageStorageService(s3ObjectUrlService, s3Client, 5, 5_242_880);
    }
}
