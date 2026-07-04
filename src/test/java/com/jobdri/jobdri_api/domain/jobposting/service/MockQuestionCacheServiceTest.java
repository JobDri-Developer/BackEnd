package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.entity.MiddleClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingMockGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockQuestionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockQuestionCacheServiceTest {

    @Mock
    private DetailClassificationRepository detailClassificationRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private JobPostingAiService jobPostingAiService;

    @Mock
    private MockQuestionInflightRegistry mockQuestionInflightRegistry;

    @Mock
    private MockQuestionCacheTransactionalService mockQuestionCacheTransactionalService;

    private MockQuestionCacheService mockQuestionCacheService;

    @BeforeEach
    void setUp() {
        this.mockQuestionCacheService = new MockQuestionCacheService(
                detailClassificationRepository,
                companyRepository,
                jobPostingAiService,
                mockQuestionInflightRegistry,
                mockQuestionCacheTransactionalService
        );
    }

    @Test
    @DisplayName("캐시가 있으면 AI 호출 없이 추천 질문을 반환한다")
    void getRecommendedQuestionsUsesCache() {
        DetailClassification detailClassification = createDetailClassification(10L, 100L, "백엔드", "Java/Spring");
        Company company = Company.create("선택 기업", CompanySize.MEDIUM);
        when(mockQuestionCacheTransactionalService.findQuestions(
                1L,
                100L,
                MockQuestionCacheService.PROMPT_VERSION
        ))
                .thenReturn(Optional.of(List.of("질문 1", "질문 2")));

        List<String> questions = mockQuestionCacheService.getRecommendedQuestions(
                new JobPostingMockGenerateRequest(1L, 10L, 100L)
        );

        assertThat(questions).containsExactly("질문 1", "질문 2");
        verify(jobPostingAiService, never()).generateMockRecommendedQuestions(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("캐시가 없으면 AI 생성 결과를 저장하고 반환한다")
    void createAndCacheQuestionsWhenCacheMissing() {
        DetailClassification detailClassification = createDetailClassification(10L, 100L, "백엔드", "Java/Spring");
        JobPostingMockGenerateRequest request = new JobPostingMockGenerateRequest(1L, 10L, 100L);
        JobPostingMockQuestionResponse aiResponse = new JobPostingMockQuestionResponse(List.of("질문 A", "질문 B"));

        when(mockQuestionCacheTransactionalService.findQuestions(
                1L,
                100L,
                MockQuestionCacheService.PROMPT_VERSION
        ))
                .thenReturn(Optional.empty());
        when(detailClassificationRepository.findById(100L)).thenReturn(Optional.of(detailClassification));
        when(companyRepository.findById(1L)).thenReturn(Optional.of(Company.create("선택 기업", CompanySize.MEDIUM)));
        when(jobPostingAiService.generateMockRecommendedQuestions(
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.any(Company.class)
        )).thenReturn(aiResponse);
        when(mockQuestionCacheTransactionalService.saveQuestions(
                org.mockito.ArgumentMatchers.any(Company.class),
                org.mockito.ArgumentMatchers.eq(detailClassification),
                org.mockito.ArgumentMatchers.eq(MockQuestionCacheService.PROMPT_VERSION),
                org.mockito.ArgumentMatchers.eq(List.of("질문 A", "질문 B"))
        )).thenReturn(List.of("질문 A", "질문 B"));

        List<String> questions = mockQuestionCacheService.createAndCacheQuestions(request);

        assertThat(questions).containsExactly("질문 A", "질문 B");
        verify(mockQuestionCacheTransactionalService).saveQuestions(
                org.mockito.ArgumentMatchers.any(Company.class),
                org.mockito.ArgumentMatchers.eq(detailClassification),
                org.mockito.ArgumentMatchers.eq(MockQuestionCacheService.PROMPT_VERSION),
                org.mockito.ArgumentMatchers.eq(List.of("질문 A", "질문 B"))
        );
    }

    @Test
    @DisplayName("캐시 저장 충돌 시 재조회한 캐시를 반환한다")
    void createAndCacheQuestionsReturnsRefetchedCacheWhenSaveConflicts() {
        DetailClassification detailClassification = createDetailClassification(10L, 100L, "백엔드", "Java/Spring");
        JobPostingMockGenerateRequest request = new JobPostingMockGenerateRequest(1L, 10L, 100L);
        Company company = Company.create("선택 기업", CompanySize.MEDIUM);
        JobPostingMockQuestionResponse aiResponse = new JobPostingMockQuestionResponse(List.of("질문 A", "질문 B"));

        when(mockQuestionCacheTransactionalService.findQuestions(
                1L,
                100L,
                MockQuestionCacheService.PROMPT_VERSION
        ))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(List.of("질문 A", "질문 B")));
        when(detailClassificationRepository.findById(100L)).thenReturn(Optional.of(detailClassification));
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(jobPostingAiService.generateMockRecommendedQuestions(
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq(company)
        )).thenReturn(aiResponse);
        when(mockQuestionCacheTransactionalService.saveQuestions(
                org.mockito.ArgumentMatchers.eq(company),
                org.mockito.ArgumentMatchers.eq(detailClassification),
                org.mockito.ArgumentMatchers.eq(MockQuestionCacheService.PROMPT_VERSION),
                org.mockito.ArgumentMatchers.eq(List.of("질문 A", "질문 B"))
        )).thenThrow(new DataIntegrityViolationException("uk_mock_question_cache_company_detail_version"));

        List<String> questions = mockQuestionCacheService.createAndCacheQuestions(request);

        assertThat(questions).containsExactly("질문 A", "질문 B");
    }

    @Test
    @DisplayName("캐시 미스 시 inflight registry를 통해 생성 경로로 진입한다")
    void getRecommendedQuestionsUsesInflightRegistryOnCacheMiss() {
        DetailClassification detailClassification = createDetailClassification(10L, 100L, "백엔드", "Java/Spring");
        JobPostingMockGenerateRequest request = new JobPostingMockGenerateRequest(1L, 10L, 100L);
        Company company = Company.create("선택 기업", CompanySize.MEDIUM);
        JobPostingMockQuestionResponse aiResponse = new JobPostingMockQuestionResponse(List.of("질문 A", "질문 B"));

        when(mockQuestionCacheTransactionalService.findQuestions(1L, 100L, MockQuestionCacheService.PROMPT_VERSION))
                .thenReturn(Optional.empty(), Optional.empty());
        when(mockQuestionInflightRegistry.execute(
                org.mockito.ArgumentMatchers.eq("1:100:v1"),
                org.mockito.ArgumentMatchers.any()
        )).thenAnswer(invocation -> {
            MockQuestionInflightRegistry.TaskSupplier supplier = invocation.getArgument(1);
            try {
                return supplier.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        when(detailClassificationRepository.findById(100L)).thenReturn(Optional.of(detailClassification));
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(jobPostingAiService.generateMockRecommendedQuestions(request, company)).thenReturn(aiResponse);
        when(mockQuestionCacheTransactionalService.saveQuestions(
                company,
                detailClassification,
                MockQuestionCacheService.PROMPT_VERSION,
                List.of("질문 A", "질문 B")
        )).thenReturn(List.of("질문 A", "질문 B"));

        List<String> questions = mockQuestionCacheService.getRecommendedQuestions(request);

        assertThat(questions).containsExactly("질문 A", "질문 B");
        verify(mockQuestionInflightRegistry).execute(
                org.mockito.ArgumentMatchers.eq("1:100:v1"),
                org.mockito.ArgumentMatchers.any()
        );
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
