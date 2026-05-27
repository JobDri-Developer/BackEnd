package com.jobdri.jobdri_api.domain.mockapply.service;

import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplySequence;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplySequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MockApplySequenceService {

    private final MockApplySequenceRepository mockApplySequenceRepository;
    private final MockApplyRepository mockApplyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int allocate(Long userId, Long jobPostingId) {
        MockApplySequence sequence = mockApplySequenceRepository
                .findByKeyForUpdate(userId, jobPostingId)
                .orElseGet(() -> mockApplySequenceRepository.saveAndFlush(
                        MockApplySequence.create(
                                userId,
                                jobPostingId,
                                mockApplyRepository.findMaxSequenceByUserIdAndJobPostingId(
                                        userId,
                                        jobPostingId
                                )
                        )
                ));

        return sequence.incrementAndGet();
    }
}
