package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
class AnalysisPreparationDataLoader {

    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;

    @Transactional(readOnly = true)
    LoadedPreparationData load(User user, Long mockApplyId) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        initializePreparationHierarchy(mockApply);
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        return new LoadedPreparationData(mockApply, questions);
    }

    private MockApply getOwnedMockApply(User user, Long mockApplyId) {
        MockApply mockApply = mockApplyRepository.findById(mockApplyId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.MOCK_APPLY_NOT_FOUND,
                        "해당 모의 서류 지원을 찾을 수 없습니다. mockApplyId=" + mockApplyId
                ));

        if (!mockApply.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 모의 서류 지원에 접근할 수 없습니다.");
        }
        return mockApply;
    }

    private void initializePreparationHierarchy(MockApply mockApply) {
        Hibernate.initialize(mockApply.getJobPosting());
        Hibernate.initialize(mockApply.getJobPosting().getCompany());
        Hibernate.initialize(mockApply.getJobPosting().getDetailClassification());
        Hibernate.initialize(mockApply.getJobPosting().getDetailClassification().getMiddleClassification());
        Hibernate.initialize(mockApply.getJobPosting().getDetailClassification().getMiddleClassification().getClassification());
    }

    record LoadedPreparationData(MockApply mockApply, List<Question> questions) {
    }
}
