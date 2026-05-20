package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestMultipartRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationResultResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingIngestServiceTest {

    @Mock
    private JobPostingAiService jobPostingAiService;

    @Mock
    private JobPostingClassificationService jobPostingClassificationService;

    @Mock
    private JobPostingService jobPostingService;

    @Mock
    private UserService userService;

    @InjectMocks
    private JobPostingIngestService jobPostingIngestService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.signup("테스트 사용자", "ingest@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(jobPostingIngestService, "classificationConfidenceThreshold", 0.65);
    }

    @Test
    @DisplayName("동기 ingest는 multipart 이미지와 content type을 추출 단계로 전달한다")
    void ingestAndCreatePassesMultipartImageToExtract() {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "posting.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        JobPostingIngestMultipartRequest request = new JobPostingIngestMultipartRequest(
                "채용 공고 원문",
                "https://example.com/job-posting",
                image
        );

        JobPostingExtractResponse extracted = new JobPostingExtractResponse(
                "해커스 교육그룹",
                "클라우드 엔지니어",
                "클라우드 운영",
                "경력",
                "",
                "채용 공고 원문",
                0.9
        );
        JobPostingClassificationCandidateResponse candidate = new JobPostingClassificationCandidateResponse(
                1L,
                "백엔드 개발",
                "AI·개발·데이터",
                "개발·데이터",
                0.8
        );
        JobPostingClassificationResultResponse classification = new JobPostingClassificationResultResponse(
                1L,
                "백엔드 개발",
                "AI·개발·데이터",
                "개발·데이터",
                "가장 적합한 소분류입니다.",
                0.9
        );
        JobPostingGenerateResponse generated = new JobPostingGenerateResponse(
                "해커스 교육그룹",
                "클라우드 엔지니어",
                "정제된 주요 업무",
                "정제된 자격 요건",
                "정제된 우대 사항",
                "요약"
        );
        JobPostingResponse saved = JobPostingResponse.builder()
                .jobPostingId(10L)
                .userId(1L)
                .companyId(2L)
                .companyName("해커스 교육그룹")
                .detailClassificationId(1L)
                .detailClassificationName("백엔드 개발")
                .task("정제된 주요 업무")
                .requirement("정제된 자격 요건")
                .preferred("정제된 우대 사항")
                .build();

        when(jobPostingAiService.extractJobPosting(any(), any(byte[].class), any(), any()))
                .thenReturn(extracted);
        when(jobPostingClassificationService.findCandidates(extracted, 5))
                .thenReturn(List.of(candidate));
        when(jobPostingAiService.classifyDetailClassification(extracted, List.of(candidate)))
                .thenReturn(classification);
        when(jobPostingAiService.generateJobPosting(any()))
                .thenReturn(generated);
        when(userService.getUser(1L)).thenReturn(user);
        when(jobPostingService.createJobPosting(eq(user), any(JobPostingCreateRequest.class)))
                .thenReturn(saved);

        JobPostingIngestResponse response = jobPostingIngestService.ingestAndCreate(user, request);

        ArgumentCaptor<byte[]> imageBytesCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(jobPostingAiService).extractJobPosting(
                eq("채용 공고 원문"),
                imageBytesCaptor.capture(),
                contentTypeCaptor.capture(),
                eq("https://example.com/job-posting")
        );

        assertThat(imageBytesCaptor.getValue()).containsExactly(1, 2, 3);
        assertThat(contentTypeCaptor.getValue()).isEqualTo("image/png");
        assertThat(response.isSavedToDatabase()).isTrue();
    }
}
