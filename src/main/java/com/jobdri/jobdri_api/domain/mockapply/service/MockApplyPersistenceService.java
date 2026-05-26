package com.jobdri.jobdri_api.domain.mockapply.service;

import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MockApplyPersistenceService {

    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MockApply saveAndFlush(MockApply mockApply) {
        return mockApplyRepository.saveAndFlush(mockApply);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Question> saveQuestions(List<Question> questions) {
        return questionRepository.saveAll(questions);
    }
}
