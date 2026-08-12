package com.jobdri.jobdri_api.domain.analysis.service.question;

import com.jobdri.jobdri_api.domain.analysis.entity.CustomQuestionCandidate;
import com.jobdri.jobdri_api.domain.analysis.repository.CustomQuestionCandidateRepository;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomQuestionCandidatePersistenceService {
    private final CustomQuestionCandidateRepository customQuestionCandidateRepository;
    private final EntityManager entityManager;

    public CustomQuestionCandidatePersistenceService(
            CustomQuestionCandidateRepository customQuestionCandidateRepository,
            EntityManager entityManager
    ) {
        this.customQuestionCandidateRepository = customQuestionCandidateRepository;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CustomQuestionCandidate saveAndFlush(Long mockApplyId, String content, int charLimit) {
        MockApply mockApplyReference = entityManager.getReference(MockApply.class, mockApplyId);
        return customQuestionCandidateRepository.saveAndFlush(
                CustomQuestionCandidate.create(mockApplyReference, content, charLimit)
        );
    }
}
