package com.jobdri.jobdri_api.domain.mockapply.service;

import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MockApplyPersistenceService {

    private final MockApplyRepository mockApplyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MockApply saveAndFlush(MockApply mockApply) {
        return mockApplyRepository.saveAndFlush(mockApply);
    }
}
