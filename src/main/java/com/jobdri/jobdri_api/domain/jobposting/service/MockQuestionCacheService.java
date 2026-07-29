package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingMockGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockQuestionResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MockQuestionCacheService {

    private static final String CACHE_UNIQUE_CONSTRAINT = "uk_mock_question_cache_company_detail_version";

    private final DetailClassificationRepository detailClassificationRepository;
    private final CompanyRepository companyRepository;
    private final JobPostingAiService jobPostingAiService;
    private final MockQuestionInflightRegistry mockQuestionInflightRegistry;
    private final MockQuestionDistributedLockService mockQuestionDistributedLockService;
    private final MockQuestionCacheWaitExecutor mockQuestionCacheWaitExecutor;
    private final MockQuestionCacheTransactionalService mockQuestionCacheTransactionalService;
    private final MockQuestionCacheProperties mockQuestionCacheProperties;
    private final MockQuestionCacheVersionProvider mockQuestionCacheVersionProvider;

    public List<String> getRecommendedQuestions(JobPostingMockGenerateRequest request) {
        return getCachedQuestions(request)
                .orElseGet(() -> mockQuestionInflightRegistry.execute(cacheKey(request), () -> createAndCacheQuestions(request)));
    }

    public List<String> createAndCacheQuestions(JobPostingMockGenerateRequest request) {
        return getCachedQuestions(request).orElseGet(() -> createAndCacheQuestionsWithLock(request));
    }

    private List<String> createAndCacheQuestionsWithLock(JobPostingMockGenerateRequest request) {
        String cacheKey = cacheKey(request);
        MockQuestionDistributedLockService.LockLease lockLease = mockQuestionDistributedLockService.tryAcquire(cacheKey);

        if (lockLease == null) {
            return awaitCachedQuestions(request, cacheKey);
        }

        try (lockLease) {
            return getCachedQuestions(request).orElseGet(() -> createAndCacheQuestionsInternal(request));
        }
    }

    private List<String> createAndCacheQuestionsInternal(JobPostingMockGenerateRequest request) {
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

        JobPostingMockQuestionResponse generated = jobPostingAiService.generateMockRecommendedQuestions(request, company);
        try {
            return mockQuestionCacheTransactionalService.saveQuestions(
                    company,
                    detailClassification,
                    currentPromptVersion(),
                    generated.recommendedQuestions()
            );
        } catch (DataIntegrityViolationException e) {
            if (!isCacheUniqueConflict(e)) {
                throw e;
            }
            return getCachedQuestions(request).orElseThrow(() -> e);
        }
    }

    private Optional<List<String>> getCachedQuestions(JobPostingMockGenerateRequest request) {
        return mockQuestionCacheTransactionalService.findQuestions(
                request.companyId(),
                request.detailClassificationId(),
                currentPromptVersion()
        );
    }

    private List<String> awaitCachedQuestions(JobPostingMockGenerateRequest request, String cacheKey) {
        return mockQuestionCacheWaitExecutor.execute(
                () -> awaitCachedQuestionsWithTakeover(request, cacheKey),
                mockQuestionCacheProperties.getWaitTimeoutMillis()
        );
    }

    private List<String> awaitCachedQuestionsWithTakeover(JobPostingMockGenerateRequest request, String cacheKey) {
        long pollIntervalMillis = Math.max(0L, mockQuestionCacheProperties.getPollIntervalMillis());
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(
                Math.max(0L, mockQuestionCacheProperties.getWaitTimeoutMillis())
        );

        Optional<List<String>> cachedQuestions = getCachedQuestions(request);
        while (cachedQuestions.isEmpty() && System.nanoTime() < deadline) {
            MockQuestionDistributedLockService.LockLease takeoverLease = mockQuestionDistributedLockService.tryAcquire(cacheKey);
            if (takeoverLease != null) {
                try (takeoverLease) {
                    return getCachedQuestions(request).orElseGet(() -> createAndCacheQuestionsInternal(request));
                }
            }

            pauseBeforeRetry(pollIntervalMillis);
            cachedQuestions = getCachedQuestions(request);
        }

        return cachedQuestions.orElseThrow(() -> new GeneralException(
                GeneralErrorCode.SERVICE_UNAVAILABLE,
                "추천 질문 생성이 처리 중입니다. 잠시 후 다시 시도해주세요."
        ));
    }

    private String cacheKey(JobPostingMockGenerateRequest request) {
        return request.companyId() + ":" + request.detailClassificationId() + ":" + currentPromptVersion();
    }

    private String currentPromptVersion() {
        return mockQuestionCacheVersionProvider.currentVersion();
    }

    private void pauseBeforeRetry(long pollIntervalMillis) {
        try {
            Thread.sleep(pollIntervalMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "추천 질문 생성 대기 중 인터럽트가 발생했습니다."
            );
        }
    }

    private boolean isCacheUniqueConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraintViolation
                    && containsConstraintName(constraintViolation.getConstraintName())) {
                return true;
            }
            if (containsConstraintName(cause.getMessage())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean containsConstraintName(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(CACHE_UNIQUE_CONSTRAINT);
    }
}
