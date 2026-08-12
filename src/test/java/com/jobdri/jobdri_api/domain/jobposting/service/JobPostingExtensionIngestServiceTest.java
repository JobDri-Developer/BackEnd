package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingExtensionIngestRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtensionIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingExtensionIngestServiceTest {

    @Mock
    private JobPostingIngestService jobPostingIngestService;

    @InjectMocks
    private JobPostingExtensionIngestService ingestService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.signup("테스트 사용자", "extension@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    @DisplayName("익스텐션 공고 수집 성공 시 저장만 수행하고 mock 지원은 생성하지 않는다")
    void ingestDoesNotCreateMockApplyWhenJobPostingSaved() {
        JobPostingExtensionIngestRequest request = new JobPostingExtensionIngestRequest(
                "https://www.wanted.co.kr/wd/123",
                "WANTED",
                "채용 공고 원문"
        );
        JobPostingResponse saved = JobPostingResponse.builder()
                .jobPostingId(10L)
                .userId(1L)
                .companyId(2L)
                .companyName("테스트 회사")
                .detailClassificationId(3L)
                .detailClassificationName("백엔드 개발")
                .task("주요 업무")
                .requirement("자격 요건")
                .preferred("우대 사항")
                .build();
        JobPostingIngestResponse ingest = new JobPostingIngestResponse(
                true,
                "저장 성공",
                null,
                null,
                null,
                null,
                saved
        );
        when(jobPostingIngestService.ingestAndCreate(eq(user), org.mockito.ArgumentMatchers.any(JobPostingIngestRequest.class)))
                .thenReturn(ingest);

        JobPostingExtensionIngestResponse response = ingestService.ingest(user, request);

        ArgumentCaptor<JobPostingIngestRequest> ingestRequestCaptor = ArgumentCaptor.forClass(JobPostingIngestRequest.class);
        verify(jobPostingIngestService).ingestAndCreate(eq(user), ingestRequestCaptor.capture());
        assertThat(ingestRequestCaptor.getValue().rawText()).isEqualTo("채용 공고 원문");
        assertThat(ingestRequestCaptor.getValue().imageObjectKey()).isNull();
        assertThat(response.sourceUrl()).isEqualTo("https://www.wanted.co.kr/wd/123");
        assertThat(response.sourceSite()).isEqualTo("WANTED");
        assertThat(response.mockApply()).isNull();
    }

    @Test
    @DisplayName("공고 저장이 보류되면 모의 서류 지원을 생성하지 않는다")
    void ingestSkipsMockApplyWhenJobPostingNotSaved() {
        JobPostingExtensionIngestRequest request = new JobPostingExtensionIngestRequest(
                "https://www.wanted.co.kr/wd/123",
                "WANTED",
                "채용 공고 원문"
        );
        JobPostingIngestResponse ingest = new JobPostingIngestResponse(
                false,
                "저장 보류",
                null,
                null,
                null,
                null,
                null
        );

        when(jobPostingIngestService.ingestAndCreate(eq(user), org.mockito.ArgumentMatchers.any(JobPostingIngestRequest.class)))
                .thenReturn(ingest);

        JobPostingExtensionIngestResponse response = ingestService.ingest(user, request);

        assertThat(response.savedToDatabase()).isFalse();
        assertThat(response.mockApply()).isNull();
    }

    @Test
    @DisplayName("공고 저장 성공 응답에 저장 공고가 없으면 모의 서류 지원을 생성하지 않는다")
    void ingestSkipsMockApplyWhenSavedJobPostingIsNull() {
        JobPostingExtensionIngestRequest request = new JobPostingExtensionIngestRequest(
                "https://www.wanted.co.kr/wd/123",
                "WANTED",
                "채용 공고 원문"
        );
        JobPostingIngestResponse ingest = new JobPostingIngestResponse(
                true,
                "저장 성공",
                null,
                null,
                null,
                null,
                null
        );

        when(jobPostingIngestService.ingestAndCreate(eq(user), org.mockito.ArgumentMatchers.any(JobPostingIngestRequest.class)))
                .thenReturn(ingest);

        JobPostingExtensionIngestResponse response = ingestService.ingest(user, request);

        assertThat(response.savedToDatabase()).isTrue();
        assertThat(response.mockApply()).isNull();
    }
}
