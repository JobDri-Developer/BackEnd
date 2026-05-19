package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.entity.MiddleClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingMockGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockQuestionResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingAiServiceTest {

    private static final Company TEST_COMPANY = Company.create("선택 기업", CompanySize.MEDIUM);
    private static final com.jobdri.jobdri_api.domain.user.entity.User TEST_USER =
            com.jobdri.jobdri_api.domain.user.entity.User.signup("테스트 사용자", "test-user@example.com", "encoded-password");

    @Mock
    private OpenAIClient openAIClient;

    @Mock
    private DetailClassificationRepository detailClassificationRepository;

    @Mock
    private JobPostingRepository jobPostingRepository;

    private JobPostingAiService jobPostingAiService;

    @BeforeEach
    void setUp() {
        jobPostingAiService = new JobPostingAiService(
                openAIClient,
                detailClassificationRepository,
                jobPostingRepository
        );
        ReflectionTestUtils.setField(TEST_COMPANY, "id", 1L);
        ReflectionTestUtils.setField(TEST_USER, "id", 1L);
        ReflectionTestUtils.setField(jobPostingAiService, "extractionModel", "gpt-4o-mini");
    }

    @Test
    @DisplayName("존재하지 않는 소분류 ID로 모의 공고 생성 시 예외를 던진다")
    void generateMockJobPostingThrowsWhenDetailClassificationNotFound() {
        when(detailClassificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobPostingAiService.generateMockJobPosting(
                new JobPostingMockGenerateRequest(1L, 10L, 999L),
                TEST_COMPANY
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.CLASSIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("소분류가 요청 중분류 하위가 아니면 예외를 던진다")
    void generateMockJobPostingThrowsWhenDetailDoesNotBelongToMiddle() {
        DetailClassification detailClassification = createDetailClassification(10L, 100L, "백엔드", "Java/Spring");
        when(detailClassificationRepository.findById(100L)).thenReturn(Optional.of(detailClassification));

        assertThatThrownBy(() -> jobPostingAiService.generateMockJobPosting(
                new JobPostingMockGenerateRequest(1L, 11L, 100L),
                TEST_COMPANY
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.CLASSIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("기존 공고가 없으면 분류명 기반 fallback 모의 공고를 생성한다")
    void generateMockJobPostingUsesFallbackWhenNoReferencePostings() {
        DetailClassification detailClassification = createDetailClassification(10L, 100L, "백엔드", "Java/Spring");
        when(detailClassificationRepository.findById(100L)).thenReturn(Optional.of(detailClassification));
        when(jobPostingRepository.findTop5ReferencePostings(1L, 100L)).thenReturn(List.of());

        JobPostingMockGenerateResponse response = jobPostingAiService.generateMockJobPosting(
                new JobPostingMockGenerateRequest(1L, 10L, 100L),
                TEST_COMPANY
        );

        assertThat(response.companyName()).isEqualTo("선택 기업");
        assertThat(response.jobTitle()).isEqualTo("Java/Spring");
        assertThat(response.task()).contains("Java/Spring");
        assertThat(response.summary()).contains("백엔드", "Java/Spring");
    }

    @Test
    @DisplayName("기존 공고가 있으면 fallback에서도 참고 공고 내용을 반영한다")
    void generateMockJobPostingUsesReferencePostingFallback() {
        DetailClassification detailClassification = createDetailClassification(10L, 100L, "데이터", "데이터 분석");
        JobPosting referencePosting = JobPosting.create(
                TEST_USER,
                Company.create("참고 기업", CompanySize.MEDIUM),
                detailClassification,
                "기존 주요 업무",
                "기존 자격 요건",
                "기존 우대 사항"
        );
        when(detailClassificationRepository.findById(100L)).thenReturn(Optional.of(detailClassification));
        when(jobPostingRepository.findTop5ReferencePostings(1L, 100L)).thenReturn(List.of(referencePosting));

        JobPostingMockGenerateResponse response = jobPostingAiService.generateMockJobPosting(
                new JobPostingMockGenerateRequest(1L, 10L, 100L),
                TEST_COMPANY
        );

        assertThat(response.companyName()).isEqualTo("선택 기업");
        assertThat(response.task()).isEqualTo("기존 주요 업무");
        assertThat(response.requirement()).isEqualTo("기존 자격 요건");
        assertThat(response.preferred()).isEqualTo("기존 우대 사항");
        assertThat(response.recommendedQuestions()).isEmpty();
    }

    @Test
    @DisplayName("같은 회사와 소분류 공고가 있으면 그 공고를 우선 참고한다")
    void generateMockJobPostingPrefersCompanyAndDetailReferences() {
        DetailClassification detailClassification = createDetailClassification(10L, 100L, "데이터", "데이터 분석");
        JobPosting companySpecificPosting = JobPosting.create(
                TEST_USER,
                Company.create("선택 기업", CompanySize.MEDIUM),
                detailClassification,
                "회사 맞춤 주요 업무",
                "회사 맞춤 자격 요건",
                "회사 맞춤 우대 사항"
        );
        when(detailClassificationRepository.findById(100L)).thenReturn(Optional.of(detailClassification));
        when(jobPostingRepository.findTop5ReferencePostings(1L, 100L))
                .thenReturn(List.of(companySpecificPosting));

        JobPostingMockGenerateResponse response = jobPostingAiService.generateMockJobPosting(
                new JobPostingMockGenerateRequest(1L, 10L, 100L),
                TEST_COMPANY
        );

        assertThat(response.task()).isEqualTo("회사 맞춤 주요 업무");
        assertThat(response.requirement()).isEqualTo("회사 맞춤 자격 요건");
        assertThat(response.preferred()).isEqualTo("회사 맞춤 우대 사항");
    }

    @Test
    @DisplayName("추천 질문 생성 실패 시 소분류 기반 fallback 질문을 반환한다")
    void generateMockRecommendedQuestionsUsesFallback() {
        DetailClassification detailClassification = createDetailClassification(10L, 100L, "백엔드", "Java/Spring");
        when(detailClassificationRepository.findById(100L)).thenReturn(Optional.of(detailClassification));
        when(jobPostingRepository.findTop5ReferencePostings(null, 100L)).thenReturn(List.of());

        JobPostingMockQuestionResponse response = jobPostingAiService.generateMockRecommendedQuestions(
                new JobPostingMockGenerateRequest(1L, 10L, 100L)
        );

        assertThat(response.recommendedQuestions()).hasSize(5);
        assertThat(response.recommendedQuestions().getFirst()).contains("Java/Spring");
    }

    @Test
    @DisplayName("점수화된 참고 공고 목록의 첫 공고를 우선 사용한다")
    void generateMockJobPostingUsesTopScoredReferenceFirst() {
        DetailClassification detailClassification = createDetailClassification(10L, 100L, "백엔드", "Java/Spring");
        JobPosting topScoredPosting = JobPosting.create(
                TEST_USER,
                Company.create("선택 기업", CompanySize.MEDIUM),
                detailClassification,
                "회사 기반 주요 업무",
                "회사 기반 자격 요건",
                "회사 기반 우대 사항"
        );
        JobPosting lowerPriorityPosting = JobPosting.create(
                TEST_USER,
                Company.create("다른 기업", CompanySize.MEDIUM),
                detailClassification,
                "직무 기반 주요 업무",
                "직무 기반 자격 요건",
                "직무 기반 우대 사항"
        );
        when(detailClassificationRepository.findById(100L)).thenReturn(Optional.of(detailClassification));
        when(jobPostingRepository.findTop5ReferencePostings(1L, 100L))
                .thenReturn(List.of(topScoredPosting, lowerPriorityPosting));

        JobPostingMockGenerateResponse response = jobPostingAiService.generateMockJobPosting(
                new JobPostingMockGenerateRequest(1L, 10L, 100L),
                TEST_COMPANY
        );

        assertThat(response.task()).isEqualTo("회사 기반 주요 업무");
        assertThat(response.requirement()).isEqualTo("회사 기반 자격 요건");
        assertThat(response.preferred()).isEqualTo("회사 기반 우대 사항");
    }

    @Test
    @DisplayName("기존 공고 생성에서 companySize가 null이어도 NPE 없이 fallback 응답을 반환한다")
    void generateJobPostingDoesNotThrowWhenCompanySizeIsNull() {
        DetailClassification detailClassification = createDetailClassification(10L, 100L, "백엔드", "Java/Spring");
        when(detailClassificationRepository.findById(100L)).thenReturn(Optional.of(detailClassification));
        JobPostingGenerateRequest request = new JobPostingGenerateRequest(
                "테스트 기업",
                null,
                100L,
                "채용 요약",
                "Java, Spring",
                "주요 업무",
                "자격 요건",
                "우대 사항",
                "담백하게",
                "백엔드 개발자"
        );

        JobPostingGenerateResponse response = jobPostingAiService.generateJobPosting(request);

        assertThat(response.companyName()).isEqualTo("테스트 기업");
        assertThat(response.jobTitle()).isEqualTo("백엔드 개발자");
    }

    private DetailClassification createDetailClassification(
            Long middleClassificationId,
            Long detailClassificationId,
            String middleName,
            String detailName
    ) {
        Classification classification = Classification.create("개발");
        MiddleClassification middleClassification = classification.addMiddleClassification(middleName);
        DetailClassification detailClassification = middleClassification.addDetailClassification(detailName);
        ReflectionTestUtils.setField(middleClassification, "id", middleClassificationId);
        ReflectionTestUtils.setField(detailClassification, "id", detailClassificationId);
        return detailClassification;
    }
}
