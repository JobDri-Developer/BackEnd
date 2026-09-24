package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationIngestRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationIngestResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationResultResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAiService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingClassificationService;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingImageStorageService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobApplicationIngestServiceTest {
    @Mock JobPostingAiService jobPostingAiService;
    @Mock JobPostingClassificationService jobPostingClassificationService;
    @Mock JobPostingImageStorageService jobPostingImageStorageService;
    @Mock JobApplicationIngestPersistenceService persistenceService;
    @InjectMocks JobApplicationIngestService service;

    private User user;
    private JobPostingExtractResponse extracted;
    private JobPostingClassificationCandidateResponse candidate;

    @BeforeEach
    void setUp() {
        user = User.signup("사용자", "clipper-ingest@example.com", "password");
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(service, "classificationConfidenceThreshold", 0.65);
        extracted = new JobPostingExtractResponse(
                "백엔드 채용", "잡드리", "백엔드 엔지니어", "API 개발", "Java 경험", "AWS 경험",
                "잡드리 백엔드 엔지니어 채용 공고 원문", 0.9,
                List.of("Java", "Spring"), "2026-10-31T18:00:00"
        );
        candidate = new JobPostingClassificationCandidateResponse(10L, "백엔드", "개발", "IT", 0.8);
    }

    @Test
    @DisplayName("텍스트와 이미지 추출·분류를 재사용하고 JobApplication 저장 경로만 호출한다")
    void ingestCreatesOnlyJobApplication() {
        JobApplicationIngestRequest request = new JobApplicationIngestRequest(
                "clipper-request-1", "잡드리 백엔드 채용 공고 원문입니다.", null,
                List.of("job-postings/tmp/1/first.png", "job-postings/tmp/1/second.png")
        );
        JobPostingClassificationResultResponse classification = classification(0.91);
        JobPostingGenerateResponse generated = generated();
        JobApplicationResponse application = JobApplicationResponse.builder().jobApplicationId(20L).build();

        when(persistenceService.findExisting(user, request.idempotencyKey())).thenReturn(Optional.empty());
        when(jobPostingImageStorageService.normalizeImageObjectKeys(null, request.imageObjectKeys()))
                .thenReturn(request.imageObjectKeys());
        when(jobPostingAiService.extractJobPosting(
                1L, request.rawText(), request.imageObjectKeys().get(0), request.imageObjectKeys()))
                .thenReturn(extracted);
        when(jobPostingClassificationService.findCandidates(extracted, 5)).thenReturn(List.of(candidate));
        when(jobPostingAiService.classifyDetailClassification(extracted, List.of(candidate))).thenReturn(classification);
        when(jobPostingAiService.generateJobPosting(any())).thenReturn(generated);
        when(persistenceService.persist(user, request.idempotencyKey(), 10L, extracted, generated))
                .thenReturn(new JobApplicationIngestPersistenceService.PersistResult(application, false));

        JobApplicationIngestResponse response = service.ingest(user, request);

        assertThat(response.savedToDatabase()).isTrue();
        assertThat(response.idempotentReplay()).isFalse();
        assertThat(response.jobApplication().getJobApplicationId()).isEqualTo(20L);
        verify(persistenceService).persist(user, "clipper-request-1", 10L, extracted, generated);
    }

    @Test
    @DisplayName("분류 confidence가 낮으면 일관된 보류 응답을 반환하고 저장하지 않는다")
    void lowConfidenceDoesNotPersist() {
        JobApplicationIngestRequest request = new JobApplicationIngestRequest(
                "clipper-request-low", "잡드리 백엔드 채용 공고 원문입니다.", null, null
        );
        when(persistenceService.findExisting(user, request.idempotencyKey())).thenReturn(Optional.empty());
        when(jobPostingImageStorageService.normalizeImageObjectKeys(null, null)).thenReturn(List.of());
        when(jobPostingAiService.extractJobPosting(1L, request.rawText(), null, List.of())).thenReturn(extracted);
        when(jobPostingClassificationService.findCandidates(extracted, 5)).thenReturn(List.of(candidate));
        when(jobPostingAiService.classifyDetailClassification(extracted, List.of(candidate)))
                .thenReturn(classification(0.64));

        JobApplicationIngestResponse response = service.ingest(user, request);

        assertThat(response.savedToDatabase()).isFalse();
        assertThat(response.jobApplication()).isNull();
        assertThat(response.message()).contains("confidence");
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("분류 후보가 없으면 기존 오류 계약을 사용하고 저장하지 않는다")
    void missingClassificationCandidateDoesNotPersist() {
        JobApplicationIngestRequest request = new JobApplicationIngestRequest(
                "clipper-request-empty", "잡드리 백엔드 채용 공고 원문입니다.", null, null
        );
        when(persistenceService.findExisting(user, request.idempotencyKey())).thenReturn(Optional.empty());
        when(jobPostingImageStorageService.normalizeImageObjectKeys(null, null)).thenReturn(List.of());
        when(jobPostingAiService.extractJobPosting(1L, request.rawText(), null, List.of())).thenReturn(extracted);
        when(jobPostingClassificationService.findCandidates(extracted, 5)).thenReturn(List.of());

        assertThatThrownBy(() -> service.ingest(user, request))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.CLASSIFICATION_NOT_FOUND);
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("이미 처리한 멱등 키는 AI 호출 없이 기존 카드를 반환한다")
    void replayReturnsExistingWithoutAi() {
        JobApplicationIngestRequest request = new JobApplicationIngestRequest(
                "clipper-request-replay", "달라진 입력이어도 기존 요청 결과를 반환합니다.", null, null
        );
        JobApplicationResponse application = JobApplicationResponse.builder().jobApplicationId(30L).build();
        when(persistenceService.findExisting(user, request.idempotencyKey())).thenReturn(Optional.of(application));

        JobApplicationIngestResponse response = service.ingest(user, request);

        assertThat(response.savedToDatabase()).isTrue();
        assertThat(response.idempotentReplay()).isTrue();
        assertThat(response.jobApplication().getJobApplicationId()).isEqualTo(30L);
        verifyNoInteractions(jobPostingAiService, jobPostingClassificationService, jobPostingImageStorageService);
    }

    private JobPostingClassificationResultResponse classification(double confidence) {
        return new JobPostingClassificationResultResponse(10L, "백엔드", "개발", "IT", "적합", confidence);
    }

    private JobPostingGenerateResponse generated() {
        return new JobPostingGenerateResponse(
                "백엔드 채용", "잡드리", "백엔드 엔지니어", "API 개발", "Java 경험", "AWS 경험", "요약"
        );
    }
}
