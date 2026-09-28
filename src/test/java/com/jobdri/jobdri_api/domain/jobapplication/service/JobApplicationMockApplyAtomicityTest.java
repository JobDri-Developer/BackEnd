package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.entity.MiddleClassification;
import com.jobdri.jobdri_api.domain.classification.repository.ClassificationRepository;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationCreateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.mockapply.service.MockApplyService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobApplicationMockApplyAtomicityTest {
    @Autowired JobApplicationMockApplyService conversionService;
    @Autowired JobApplicationService applicationService;
    @Autowired UserRepository userRepository;
    @Autowired ClassificationRepository classificationRepository;
    @Autowired JobPostingRepository jobPostingRepository;
    @Autowired MockApplyRepository mockApplyRepository;
    @MockitoBean MockApplyService mockApplyService;

    @Test
    @DisplayName("MockApply 생성 실패 시 앞서 만든 JobPosting도 함께 롤백한다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollbackPostingWhenMockApplyCreationFails() {
        User user = userRepository.save(User.signup(
                "사용자", "conversion-rollback-" + UUID.randomUUID() + "@example.com", "password"
        ));
        DetailClassification detail = saveClassification();
        JobApplicationResponse card = applicationService.create(user, new JobApplicationCreateRequest(
                "롤백 기업", "롤백 공고", "롤백 직무", null, detail.getId(),
                "롤백 업무", "롤백 자격", "롤백 우대", List.of(), null, null, null, null
        ));
        long postingsBefore = jobPostingRepository.count();
        long mockAppliesBefore = mockApplyRepository.count();
        doThrow(new IllegalStateException("mock apply failure"))
                .when(mockApplyService).createActualApply(any(User.class), any(JobPosting.class));

        assertThatThrownBy(() -> conversionService.createOrGet(user, card.getJobApplicationId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mock apply failure");
        assertThat(jobPostingRepository.count()).isEqualTo(postingsBefore);
        assertThat(mockApplyRepository.count()).isEqualTo(mockAppliesBefore);
    }

    private DetailClassification saveClassification() {
        Classification classification = Classification.create("롤백 대분류 " + UUID.randomUUID());
        MiddleClassification middle = classification.addMiddleClassification("롤백 중분류");
        DetailClassification detail = middle.addDetailClassification("롤백 소분류");
        classificationRepository.saveAndFlush(classification);
        return detail;
    }
}
