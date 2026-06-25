package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingExtensionIngestRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtensionIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyCreateResponse;
import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
import com.jobdri.jobdri_api.domain.mockapply.service.MockApplyService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingExtensionIngestServiceTest {

    @Mock
    private JobPostingIngestService jobPostingIngestService;

    @Mock
    private MockApplyService mockApplyService;

    @InjectMocks
    private JobPostingExtensionIngestService jobPostingExtensionIngestService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.signup("테스트 사용자", "extension@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    @DisplayName("익스텐션 공고 수집 성공 시 공고 저장 후 모의 서류 지원을 생성한다")
    void ingestCreatesMockApplyWhenJobPostingSaved() {
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
        MockApplyCreateResponse mockApply = new MockApplyCreateResponse(10L, 20L, ApplyType.MOCK, 1);

        when(jobPostingIngestService.ingestAndCreate(eq(user), org.mockito.ArgumentMatchers.any(JobPostingIngestRequest.class)))
                .thenReturn(ingest);
        when(mockApplyService.createMockApplyFromJobPosting(user, 10L))
                .thenReturn(mockApply);

        JobPostingExtensionIngestResponse response = jobPostingExtensionIngestService.ingest(user, request);

        ArgumentCaptor<JobPostingIngestRequest> ingestRequestCaptor = ArgumentCaptor.forClass(JobPostingIngestRequest.class);
        verify(jobPostingIngestService).ingestAndCreate(eq(user), ingestRequestCaptor.capture());
        assertThat(ingestRequestCaptor.getValue().rawText()).isEqualTo("채용 공고 원문");
        assertThat(ingestRequestCaptor.getValue().imageObjectKey()).isNull();
        assertThat(response.sourceUrl()).isEqualTo("https://www.wanted.co.kr/wd/123");
        assertThat(response.sourceSite()).isEqualTo("WANTED");
        assertThat(response.mockApply()).isEqualTo(mockApply);
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

        JobPostingExtensionIngestResponse response = jobPostingExtensionIngestService.ingest(user, request);

        verify(mockApplyService, never()).createMockApplyFromJobPosting(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(response.savedToDatabase()).isFalse();
        assertThat(response.mockApply()).isNull();
    }
}
