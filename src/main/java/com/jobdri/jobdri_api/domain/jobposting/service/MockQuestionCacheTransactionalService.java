package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.jobposting.entity.MockQuestionCache;
import com.jobdri.jobdri_api.domain.jobposting.repository.MockQuestionCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MockQuestionCacheTransactionalService {

    private final MockQuestionCacheRepository mockQuestionCacheRepository;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<List<String>> findQuestions(Long companyId, Long detailClassificationId, String promptVersion) {
        return mockQuestionCacheRepository
                .findByCompany_IdAndDetailClassification_IdAndPromptVersion(
                        companyId,
                        detailClassificationId,
                        promptVersion
                )
                .map(cache -> List.copyOf(cache.getQuestions()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> saveQuestions(
            Company company,
            DetailClassification detailClassification,
            String promptVersion,
            List<String> questions
    ) {
        MockQuestionCache saved = mockQuestionCacheRepository.saveAndFlush(
                MockQuestionCache.create(
                        company,
                        detailClassification,
                        promptVersion,
                        questions
                )
        );
        return List.copyOf(saved.getQuestions());
    }
}
