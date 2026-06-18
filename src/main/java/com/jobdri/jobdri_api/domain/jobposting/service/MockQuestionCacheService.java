package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingMockGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockQuestionResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.MockQuestionCache;
import com.jobdri.jobdri_api.domain.jobposting.repository.MockQuestionCacheRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MockQuestionCacheService {

    static final String PROMPT_VERSION = "v1";

    private final MockQuestionCacheRepository mockQuestionCacheRepository;
    private final DetailClassificationRepository detailClassificationRepository;
    private final CompanyRepository companyRepository;
    private final JobPostingAiService jobPostingAiService;

    public List<String> getRecommendedQuestions(JobPostingMockGenerateRequest request) {
        return mockQuestionCacheRepository
                .findByCompany_IdAndDetailClassification_IdAndPromptVersion(
                        request.companyId(),
                        request.detailClassificationId(),
                        PROMPT_VERSION
                )
                .map(this::copyQuestions)
                .orElseGet(() -> createAndCacheQuestions(request));
    }

    public List<String> createAndCacheQuestions(JobPostingMockGenerateRequest request) {
        return mockQuestionCacheRepository
                .findByCompany_IdAndDetailClassification_IdAndPromptVersion(
                        request.companyId(),
                        request.detailClassificationId(),
                        PROMPT_VERSION
                )
                .map(this::copyQuestions)
                .orElseGet(() -> {
                    DetailClassification detailClassification = detailClassificationRepository.findById(request.detailClassificationId())
                            .orElseThrow(() -> new GeneralException(
                                    GeneralErrorCode.CLASSIFICATION_NOT_FOUND,
                                    "해당 소분류를 찾을 수 없습니다. detailClassificationId=" + request.detailClassificationId()
                            ));
                    Company company = companyRepository.findById(request.companyId())
                            .orElseThrow(() -> new GeneralException(
                                    GeneralErrorCode.COMPANY_NOT_FOUND,
                                    "해당 회사를 찾을 수 없습니다. companyId=" + request.companyId()
                            ));

                    JobPostingMockQuestionResponse generated =
                            jobPostingAiService.generateMockRecommendedQuestions(request, company);
                    MockQuestionCache saved = mockQuestionCacheRepository.save(
                            MockQuestionCache.create(
                                    company,
                                    detailClassification,
                                    PROMPT_VERSION,
                                    generated.recommendedQuestions()
                            )
                    );
                    return copyQuestions(saved);
                });
    }

    private List<String> copyQuestions(MockQuestionCache cache) {
        return List.copyOf(cache.getQuestions());
    }
}
