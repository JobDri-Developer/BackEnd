package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationResultResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestValidationErrorResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private JobPostingImageStorageService jobPostingImageStorageService;

    @InjectMocks
    private JobPostingIngestService jobPostingIngestService;

    private User user;

    private static Stream<Arguments> invalidConfidenceValues() {
        return Stream.of(
                Arguments.of(-0.1),
                Arguments.of(1.001),
                Arguments.of(Double.NaN),
                Arguments.of(Double.POSITIVE_INFINITY),
                Arguments.of(Double.NEGATIVE_INFINITY)
        );
    }

    private static Stream<Arguments> invalidExtractedFieldValues() {
        return Stream.of(
                Arguments.of(null, "백엔드 개발자"),
                Arguments.of("잡", "백엔드 개발자"),
                Arguments.of("미분류 회사", "백엔드 개발자"),
                Arguments.of("잡드리", null),
                Arguments.of("잡드리", "백"),
                Arguments.of("잡드리", "string")
        );
    }

    private static Stream<Arguments> invalidGeneratedFieldValues() {
        return Stream.of(
                Arguments.of(null, "백엔드 개발자"),
                Arguments.of("잡", "백엔드 개발자"),
                Arguments.of("미분류 회사", "백엔드 개발자"),
                Arguments.of("잡드리", null),
                Arguments.of("잡드리", "백"),
                Arguments.of("잡드리", "string")
        );
    }

    @BeforeEach
    void setUp() {
        user = User.signup("테스트 사용자", "ingest@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(jobPostingIngestService, "classificationConfidenceThreshold", 0.65);
        lenient().when(jobPostingImageStorageService.normalizeImageObjectKeys(any(), any()))
                .thenAnswer(invocation -> {
                    String imageObjectKey = invocation.getArgument(0);
                    List<String> imageObjectKeys = invocation.getArgument(1);
                    List<String> normalized = new ArrayList<>();
                    if (imageObjectKey != null && !imageObjectKey.isBlank()) {
                        normalized.add(imageObjectKey.trim());
                    }
                    if (imageObjectKeys != null) {
                        imageObjectKeys.stream()
                                .filter(key -> key != null && !key.isBlank())
                                .map(String::trim)
                                .forEach(normalized::add);
                    }
                    return normalized;
                });
    }

    @Test
    @DisplayName("동기 ingest는 image object key를 추출 단계로 전달한다")
    void ingestAndCreatePassesImageObjectKeyToExtract() {
        JobPostingIngestRequest request = new JobPostingIngestRequest(
                "채용 공고 원문",
                "job-postings/1/posting.png"
        );

        JobPostingExtractResponse extracted = new JobPostingExtractResponse(
                "2026 해커스 클라우드 엔지니어 공개채용",
                "해커스 교육그룹",
                "클라우드 엔지니어",
                "클라우드 운영",
                "클라우드 운영 경력",
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
                "클라우드 엔지니어 채용",
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

        when(jobPostingAiService.extractJobPosting(any(), any(), any(), any()))
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

        ArgumentCaptor<String> imageObjectKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(jobPostingAiService).extractJobPosting(
                eq(1L),
                eq("채용 공고 원문"),
                imageObjectKeyCaptor.capture(),
                eq(List.of("job-postings/1/posting.png"))
        );
        ArgumentCaptor<JobPostingCreateRequest> createRequestCaptor =
                ArgumentCaptor.forClass(JobPostingCreateRequest.class);
        verify(jobPostingService).createJobPosting(eq(user), createRequestCaptor.capture());

        assertThat(imageObjectKeyCaptor.getValue()).isEqualTo("job-postings/1/posting.png");
        ArgumentCaptor<JobPostingGenerateRequest> generateRequestCaptor =
                ArgumentCaptor.forClass(JobPostingGenerateRequest.class);
        verify(jobPostingAiService).generateJobPosting(generateRequestCaptor.capture());
        assertThat(generateRequestCaptor.getValue().postingNameHint())
                .isEqualTo("2026 해커스 클라우드 엔지니어 공개채용");
        assertThat(createRequestCaptor.getValue().postingName()).isEqualTo("클라우드 엔지니어 채용");
        assertThat(createRequestCaptor.getValue().jobTitle()).isEqualTo("클라우드 엔지니어");
        assertThat(response.isSavedToDatabase()).isTrue();
    }

    @Test
    @DisplayName("동기 ingest는 이미지 없는 10자 미만 입력이면 AI 추출을 시작하지 않는다")
    void ingestAndCreateRejectsShortRawTextBeforeExtract() {
        JobPostingIngestRequest request = new JobPostingIngestRequest("짧음", null);

        assertThatThrownBy(() -> jobPostingIngestService.ingestAndCreate(user, request))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);

        verifyNoInteractions(jobPostingAiService, jobPostingClassificationService, jobPostingService, userService);
    }

    @Test
    @DisplayName("추출 confidence 경계값과 필드 최소 길이를 만족하면 저장한다")
    void ingestAndCreateAcceptsBoundaryValidExtractedResult() {
        JobPostingIngestRequest request = new JobPostingIngestRequest(
                "백엔드 개발자 채용 공고 원문입니다. 주요 업무는 API 개발이고 자격 요건은 Spring 경험입니다.",
                null
        );
        JobPostingExtractResponse extracted = new JobPostingExtractResponse(
                "잡드",
                "백엔",
                "업무내용1",
                "요건내용1",
                "",
                request.rawText(),
                0.3
        );
        JobPostingGenerateResponse generated = new JobPostingGenerateResponse(
                "백엔 채용",
                "잡드",
                "백엔",
                "업무내용1",
                "요건내용1",
                "",
                ""
        );
        JobPostingResponse saved = JobPostingResponse.builder()
                .jobPostingId(10L)
                .userId(1L)
                .companyName("잡드")
                .task("업무내용1")
                .requirement("요건내용1")
                .build();

        stubSuccessfulPipeline(extracted, generated, saved);

        JobPostingIngestResponse response = jobPostingIngestService.ingestAndCreate(user, request);

        assertThat(response.isSavedToDatabase()).isTrue();
        verify(jobPostingService).createJobPosting(eq(user), any(JobPostingCreateRequest.class));
    }

    @Test
    @DisplayName("공고로 인식할 수 없는 추출 결과는 저장하지 않고 오류 처리한다")
    void ingestAndCreateRejectsInvalidExtractedResult() {
        JobPostingIngestRequest request = new JobPostingIngestRequest(
                "양식에 맞지 않는 입력입니다",
                null
        );
        JobPostingExtractResponse extracted = new JobPostingExtractResponse(
                "미분류 회사",
                "string",
                "string",
                "string",
                "",
                "양식에 맞지 않는 입력입니다",
                0.9
        );

        when(jobPostingAiService.extractJobPosting(any(), any(), any(), any()))
                .thenReturn(extracted);

        assertThatThrownBy(() -> jobPostingIngestService.ingestAndCreate(user, request))
                .isInstanceOf(GeneralException.class)
                .hasMessageContaining("채용 공고 필수 정보를 인식하지 못했습니다.");

        verifyNoInteractions(jobPostingClassificationService, jobPostingService, userService);
    }

    @ParameterizedTest
    @MethodSource("invalidConfidenceValues")
    @DisplayName("추출 confidence가 0~1 범위를 벗어나거나 숫자가 아니면 저장하지 않는다")
    void ingestAndCreateRejectsInvalidExtractedConfidence(double confidence) {
        JobPostingIngestRequest request = new JobPostingIngestRequest("백엔드 개발자 채용 공고 입력입니다.", null);
        JobPostingExtractResponse extracted = validExtracted(confidence);

        when(jobPostingAiService.extractJobPosting(any(), any(), any(), any()))
                .thenReturn(extracted);

        assertThatThrownBy(() -> jobPostingIngestService.ingestAndCreate(user, request))
                .isInstanceOf(GeneralException.class)
                .hasMessageContaining("채용 공고로 인식할 수 없는 입력입니다.");

        verifyNoInteractions(jobPostingClassificationService, jobPostingService, userService);
    }

    @ParameterizedTest
    @MethodSource("invalidExtractedFieldValues")
    @DisplayName("추출 필수 필드가 null이거나 최소 길이 미만이면 저장하지 않는다")
    void ingestAndCreateRejectsInvalidExtractedField(
            String companyName,
            String jobTitle
    ) {
        JobPostingIngestRequest request = new JobPostingIngestRequest("백엔드 개발자 채용 공고 입력입니다.", null);
        JobPostingExtractResponse extracted = new JobPostingExtractResponse(
                companyName,
                jobTitle,
                "S",
                "R",
                "",
                request.rawText(),
                0.9
        );

        when(jobPostingAiService.extractJobPosting(any(), any(), any(), any()))
                .thenReturn(extracted);

        assertThatThrownBy(() -> jobPostingIngestService.ingestAndCreate(user, request))
                .isInstanceOf(GeneralException.class)
                .hasMessageContaining("채용 공고 필수 정보를 인식하지 못했습니다.");

        verifyNoInteractions(jobPostingClassificationService, jobPostingService, userService);
    }

    @Test
    @DisplayName("공고 생성 결과의 필수 3개 필드가 유효하면 업무와 자격 요건이 짧아도 저장한다")
    void ingestAndCreateAllowsInvalidDescriptionsWhenRequiredFieldsAreValid() {
        JobPostingIngestRequest request = new JobPostingIngestRequest(
                "백엔드 개발자 채용 공고 원문입니다. 주요 업무는 API 개발이고 자격 요건은 Spring 경험입니다.",
                null
        );
        JobPostingExtractResponse extracted = new JobPostingExtractResponse(
                "잡드리",
                "백엔드 개발자",
                "S",
                "R",
                "",
                request.rawText(),
                0.9
        );
        JobPostingGenerateResponse generated = new JobPostingGenerateResponse(
                "백엔드 개발자 채용",
                "잡드리",
                "백엔드 개발자",
                "string",
                "string",
                "",
                ""
        );
        JobPostingResponse saved = JobPostingResponse.builder()
                .jobPostingId(10L)
                .userId(1L)
                .companyName("잡드리")
                .task("string")
                .requirement("string")
                .build();

        stubSuccessfulPipeline(extracted, generated, saved);

        JobPostingIngestResponse response = jobPostingIngestService.ingestAndCreate(user, request);

        assertThat(response.isSavedToDatabase()).isTrue();
        verify(jobPostingService).createJobPosting(eq(user), any(JobPostingCreateRequest.class));
    }

    @Test
    @DisplayName("공고 생성 결과의 직무가 placeholder여도 공고명 invalid로 함께 보고하지 않는다")
    void ingestAndCreateRejectsInvalidGeneratedResult() {
        JobPostingIngestRequest request = new JobPostingIngestRequest(
                "백엔드 개발자 채용 공고 원문입니다. 주요 업무는 API 개발이고 자격 요건은 Spring 경험입니다.",
                null
        );
        JobPostingExtractResponse extracted = new JobPostingExtractResponse(
                "잡드리",
                "백엔드 개발자",
                "Spring 기반 API 개발",
                "Spring Boot 개발 경험",
                "",
                request.rawText(),
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
                "백엔드 개발자 채용",
                "잡드리",
                "string",
                "string",
                "string",
                "",
                ""
        );

        when(jobPostingAiService.extractJobPosting(any(), any(), any(), any()))
                .thenReturn(extracted);
        when(jobPostingClassificationService.findCandidates(extracted, 5))
                .thenReturn(List.of(candidate));
        when(jobPostingAiService.classifyDetailClassification(extracted, List.of(candidate)))
                .thenReturn(classification);
        when(jobPostingAiService.generateJobPosting(any()))
                .thenReturn(generated);

        Throwable thrown = catchThrowable(() -> jobPostingIngestService.ingestAndCreate(user, request));

        assertThat(thrown)
                .isInstanceOf(GeneralException.class)
                .hasMessageContaining("채용 공고 필수 정보를 인식하지 못했습니다.");
        assertInvalidFields((GeneralException) thrown, "jobTitle");

        verifyNoInteractions(jobPostingService, userService);
    }

    @ParameterizedTest
    @MethodSource("invalidGeneratedFieldValues")
    @DisplayName("생성 필수 필드가 null이거나 최소 길이 미만이면 저장하지 않는다")
    void ingestAndCreateRejectsInvalidGeneratedField(
            String companyName,
            String jobTitle
    ) {
        JobPostingIngestRequest request = new JobPostingIngestRequest(
                "백엔드 개발자 채용 공고 원문입니다. 주요 업무는 API 개발이고 자격 요건은 Spring 경험입니다.",
                null
        );
        JobPostingExtractResponse extracted = validExtracted(0.9);
        JobPostingGenerateResponse generated = new JobPostingGenerateResponse(
                "백엔드 개발자 채용",
                companyName,
                jobTitle,
                "T",
                "R",
                "",
                ""
        );

        stubUntilGenerated(extracted, generated);

        assertThatThrownBy(() -> jobPostingIngestService.ingestAndCreate(user, request))
                .isInstanceOf(GeneralException.class)
                .hasMessageContaining("채용 공고 필수 정보를 인식하지 못했습니다.");

        verifyNoInteractions(jobPostingService, userService);
    }

    @Test
    @DisplayName("공고 생성 결과의 공고명이 유효하지 않으면 postingName만 invalid field로 내려준다")
    void ingestAndCreateRejectsInvalidGeneratedPostingNameOnly() {
        JobPostingIngestRequest request = new JobPostingIngestRequest(
                "백엔드 개발자 채용 공고 원문입니다. 주요 업무는 API 개발이고 자격 요건은 Spring 경험입니다.",
                null
        );
        JobPostingExtractResponse extracted = validExtracted(0.9);
        JobPostingGenerateResponse generated = new JobPostingGenerateResponse(
                "string",
                "잡드리",
                "백엔드 개발자",
                "T",
                "R",
                "",
                ""
        );

        stubUntilGenerated(extracted, generated);

        Throwable thrown = catchThrowable(() -> jobPostingIngestService.ingestAndCreate(user, request));

        assertThat(thrown)
                .isInstanceOf(GeneralException.class)
                .hasMessageContaining("채용 공고 필수 정보를 인식하지 못했습니다.");
        assertInvalidFields((GeneralException) thrown, "postingName");

        verifyNoInteractions(jobPostingService, userService);
    }

    @Test
    @DisplayName("공고 추출 결과가 유효하지 않으면 invalid field 목록을 내려준다")
    void ingestAndCreateReturnsInvalidExtractedFieldNames() {
        JobPostingIngestRequest request = new JobPostingIngestRequest("양식에 맞지 않는 입력입니다", null);
        JobPostingExtractResponse extracted = new JobPostingExtractResponse(
                "미분류 회사",
                "string",
                "Spring 기반 API 개발",
                "Spring Boot 개발 경험",
                "",
                request.rawText(),
                0.9
        );

        when(jobPostingAiService.extractJobPosting(any(), any(), any(), any()))
                .thenReturn(extracted);

        Throwable thrown = catchThrowable(() -> jobPostingIngestService.ingestAndCreate(user, request));

        assertThat(thrown)
                .isInstanceOf(GeneralException.class)
                .hasMessageContaining("채용 공고 필수 정보를 인식하지 못했습니다.");
        assertInvalidFields((GeneralException) thrown, "companyName", "jobTitle");

        verifyNoInteractions(jobPostingClassificationService, jobPostingService, userService);
    }

    private void stubSuccessfulPipeline(
            JobPostingExtractResponse extracted,
            JobPostingGenerateResponse generated,
            JobPostingResponse saved
    ) {
        stubUntilGenerated(extracted, generated);
        when(userService.getUser(1L)).thenReturn(user);
        when(jobPostingService.createJobPosting(eq(user), any(JobPostingCreateRequest.class)))
                .thenReturn(saved);
    }

    private void stubUntilGenerated(JobPostingExtractResponse extracted, JobPostingGenerateResponse generated) {
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

        when(jobPostingAiService.extractJobPosting(any(), any(), any(), any()))
                .thenReturn(extracted);
        when(jobPostingClassificationService.findCandidates(extracted, 5))
                .thenReturn(List.of(candidate));
        when(jobPostingAiService.classifyDetailClassification(extracted, List.of(candidate)))
                .thenReturn(classification);
        when(jobPostingAiService.generateJobPosting(any()))
                .thenReturn(generated);
    }

    private JobPostingExtractResponse validExtracted(double confidence) {
        return new JobPostingExtractResponse(
                "잡드리",
                "백엔드 개발자",
                "Spring 기반 API 개발",
                "Spring Boot 개발 경험",
                "",
                "백엔드 개발자 채용 공고 원문입니다.",
                confidence
        );
    }

    private void assertInvalidFields(GeneralException exception, String... fields) {
        assertThat(exception.getError())
                .isInstanceOf(JobPostingIngestValidationErrorResponse.class);
        JobPostingIngestValidationErrorResponse error =
                (JobPostingIngestValidationErrorResponse) exception.getError();
        assertThat(error.invalidFields())
                .extracting(JobPostingIngestValidationErrorResponse.InvalidField::field)
                .containsExactly(fields);
    }
}
