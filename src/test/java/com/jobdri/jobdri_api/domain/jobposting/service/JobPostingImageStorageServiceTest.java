package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.config.s3.S3ObjectUrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingImageUploadPresignRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingImageStorageServiceTest {

    @Mock
    private S3ObjectUrlService s3ObjectUrlService;

    @Mock
    private S3Client s3Client;

    @Test
    @DisplayName("단일 필드와 배열 필드의 동일한 objectKey는 하나로 정규화한다")
    void normalizeImageObjectKeysDeduplicatesAcrossLegacyAndList() {
        JobPostingImageStorageService service = createService();

        List<String> result = service.normalizeImageObjectKeys(
                "job-postings/tmp/1/first.png",
                List.of("job-postings/tmp/1/first.png")
        );

        assertThat(result).containsExactly("job-postings/tmp/1/first.png");
    }

    @Test
    @DisplayName("단일 필드와 배열 필드의 서로 다른 objectKey를 순서대로 병합한다")
    void normalizeImageObjectKeysMergesLegacyAndListInOrder() {
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
    @DisplayName("배열 필드의 objectKey 입력 순서를 유지한다")
    void normalizeImageObjectKeysPreservesListOrder() {
        JobPostingImageStorageService service = createService();

        List<String> result = service.normalizeImageObjectKeys(
                null,
                List.of(
                        "job-postings/tmp/1/first.png",
                        "job-postings/tmp/1/second.jpg"
                )
        );

        assertThat(result).containsExactly(
                "job-postings/tmp/1/first.png",
                "job-postings/tmp/1/second.jpg"
        );
    }

    @Test
    @DisplayName("null, 빈 문자열, 공백 문자열은 무시한다")
    void normalizeImageObjectKeysIgnoresBlankValues() {
        JobPostingImageStorageService service = createService();

        List<String> result = service.normalizeImageObjectKeys(
                " ",
                java.util.Arrays.asList(null, "", "  ", " job-postings/tmp/1/first.png ")
        );

        assertThat(result).containsExactly("job-postings/tmp/1/first.png");
    }

    @Test
    @DisplayName("공백 제거 후 단일 필드와 배열 필드의 동일한 objectKey는 하나로 정규화한다")
    void normalizeImageObjectKeysDeduplicatesTrimmedValueAcrossFields() {
        JobPostingImageStorageService service = createService();

        List<String> result = service.normalizeImageObjectKeys(
                " job-postings/tmp/1/first.png ",
                List.of("job-postings/tmp/1/first.png")
        );

        assertThat(result).containsExactly("job-postings/tmp/1/first.png");
    }

    @Test
    @DisplayName("최종 고유 이미지가 3개이면 최대 개수 예외가 발생한다")
    void normalizeImageObjectKeysRejectsMoreThanTwoImages() {
        JobPostingImageStorageService service = createService();

        assertThatThrownBy(() -> service.normalizeImageObjectKeys(
                "job-postings/tmp/1/first.png",
                List.of("job-postings/tmp/1/second.jpg", "job-postings/tmp/1/third.png")
        ))
                .isInstanceOf(GeneralException.class)
                .hasMessage("이미지는 최대 2개까지 첨부할 수 있습니다.")
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("배열 내부에서 공백 제거 후 동일한 objectKey가 중복되면 예외가 발생한다")
    void normalizeImageObjectKeysRejectsDuplicateObjectKeysWithinList() {
        JobPostingImageStorageService service = createService();

        assertThatThrownBy(() -> service.normalizeImageObjectKeys(
                null,
                List.of("job-postings/tmp/1/first.png", " job-postings/tmp/1/first.png ")
        ))
                .isInstanceOf(GeneralException.class)
                .hasMessage("동일한 이미지 objectKey를 중복으로 전달할 수 없습니다.")
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    /** Accepts every documented format at exactly 10 MB after checking S3 metadata. */
    @ParameterizedTest
    @CsvSource({"png,image/png", "jpg,image/jpeg", "webp,image/webp"})
    void acceptsDocumentedFormatsAtExactSizeLimit(String extension, String contentType) {
        var service = createService();
        var upload = service.createUploadPresignUrl(1L,
                new JobPostingImageUploadPresignRequest("test." + extension, contentType));
        String key = upload.objectKey();
        assertThat(key).endsWith("." + extension);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentType(contentType).contentLength(10_000_000L).build());
        when(s3ObjectUrlService.createPresignedGetUrl(key)).thenReturn("readable-url");
        assertThat(service.createReadableImageUrl(1L, key)).isEqualTo("readable-url");
    }

    /** Rejects an object even one byte over the upload analysis limit. */
    @Test
    void rejectsOneByteOverLimit() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentType("image/png").contentLength(10_000_001L).build());
        assertThatThrownBy(() -> createService().createReadableImageUrl(1L, "job-postings/tmp/1/large.png"))
                .isInstanceOf(GeneralException.class)
                .hasMessage("이미지 파일 크기가 허용 범위를 초과했습니다.")
                .extracting("code").isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    /** Rejects an unsupported media type before signing an upload URL. */
    @Test
    void rejectsUnsupportedUploadType() {
        assertThatThrownBy(() -> createService().createUploadPresignUrl(1L,
                new JobPostingImageUploadPresignRequest("test.gif", "image/gif")))
                .isInstanceOf(GeneralException.class)
                .extracting("code").isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
        verifyNoInteractions(s3ObjectUrlService, s3Client);
    }

    /** Rejects another user's key without reading the underlying object. */
    @Test
    void rejectsAnotherUsersKeyBeforeReadingS3() {
        assertThatThrownBy(() -> createService().createReadableImageUrl(2L, "job-postings/tmp/1/owned.png"))
                .isInstanceOf(GeneralException.class)
                .extracting("code").isEqualTo(GeneralErrorCode.FORBIDDEN);
        verifyNoInteractions(s3ObjectUrlService, s3Client);
    }

    private JobPostingImageStorageService createService() {
        return new JobPostingImageStorageService(s3ObjectUrlService, s3Client, 5, 10_000_000);
    }
}
