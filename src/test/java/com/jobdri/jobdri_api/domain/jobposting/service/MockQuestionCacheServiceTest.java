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
import com.jobdri.jobdri_api.domain.jobposting.entity.MockQuestionCache;
import com.jobdri.jobdri_api.domain.jobposting.repository.MockQuestionCacheRepository;
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
    private MockQuestionCacheRepository mockQuestionCacheRepository;

    @Mock
    private DetailClassificationRepository detailClassificationRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private JobPostingAiService jobPostingAiService;

    @Mock
    private MockQuestionInflightRegistry mockQuestionInflightRegistry;

    private MockQuestionCacheService mockQuestionCacheService;

    @BeforeEach
    void setUp() {
        mockQuestionCacheService = new MockQuestionCacheService(
                mockQuestionCacheRepository,
                detailClassificationRepository,
                companyRepository,
                jobPostingAiService,
                mockQuestionInflightRegistry
        );
    }

    @Test
    @DisplayName("캐시가 있으면 AI 호출 없이 추천 질문을 반환한다")
    void getRecommendedQuestionsUsesCache() {
        DetailClassification detailClassification = createDetailClassification(10L, 100L, "백엔드", "Java/Spring");
        Company company = Company.create("선택 기업", CompanySize.MEDIUM);
        MockQuestionCache cache = MockQuestionCache.create(
                company,
                detailClassification,
                MockQuestionCacheService.PROMPT_VERSION,
                List.of("질문 1", "질문 2")
        );
        when(mockQuestionCacheRepository.findByCompany_IdAndDetailClassification_IdAndPromptVersion(
                1L,
                100L,
                MockQuestionCacheService.PROMPT_VERSION
        ))
                .thenReturn(Optional.of(cache));

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

        when(mockQuestionCacheRepository.findByCompany_IdAndDetailClassification_IdAndPromptVersion(
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
        when(mockQuestionCacheRepository.save(org.mockito.ArgumentMatchers.any(MockQuestionCache.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<String> questions = mockQuestionCacheService.createAndCacheQuestions(request);

        assertThat(questions).containsExactly("질문 A", "질문 B");
        verify(mockQuestionCacheRepository).save(org.mockito.ArgumentMatchers.any(MockQuestionCache.class));
    }

    @Test
    @DisplayName("캐시 저장 충돌 시 재조회한 캐시를 반환한다")
    void createAndCacheQuestionsReturnsRefetchedCacheWhenSaveConflicts() {
        DetailClassification detailClassification = createDetailClassification(10L, 100L, "백엔드", "Java/Spring");
        JobPostingMockGenerateRequest request = new JobPostingMockGenerateRequest(1L, 10L, 100L);
        Company company = Company.create("선택 기업", CompanySize.MEDIUM);
        JobPostingMockQuestionResponse aiResponse = new JobPostingMockQuestionResponse(List.of("질문 A", "질문 B"));
        MockQuestionCache savedCache = MockQuestionCache.create(
                company,
                detailClassification,
                MockQuestionCacheService.PROMPT_VERSION,
                List.of("질문 A", "질문 B")
        );

        when(mockQuestionCacheRepository.findByCompany_IdAndDetailClassification_IdAndPromptVersion(
                1L,
                100L,
                MockQuestionCacheService.PROMPT_VERSION
        ))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedCache));
        when(detailClassificationRepository.findById(100L)).thenReturn(Optional.of(detailClassification));
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(jobPostingAiService.generateMockRecommendedQuestions(
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq(company)
        )).thenReturn(aiResponse);
        when(mockQuestionCacheRepository.save(org.mockito.ArgumentMatchers.any(MockQuestionCache.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate cache"));

        List<String> questions = mockQuestionCacheService.createAndCacheQuestions(request);

        assertThat(questions).containsExactly("질문 A", "질문 B");
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
