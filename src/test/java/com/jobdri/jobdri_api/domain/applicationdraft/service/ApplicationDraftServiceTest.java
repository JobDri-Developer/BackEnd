package com.jobdri.jobdri_api.domain.applicationdraft.service;

import com.jobdri.jobdri_api.domain.applicationdraft.dto.request.ApplicationDraftUpsertRequest;
import com.jobdri.jobdri_api.domain.applicationdraft.dto.response.ApplicationDraftResponse;
import com.jobdri.jobdri_api.domain.applicationdraft.dto.response.ApplicationDraftSaveResponse;
import com.jobdri.jobdri_api.domain.applicationdraft.entity.ApplicationDraftStep;
import com.jobdri.jobdri_api.domain.applicationdraft.repository.ApplicationDraftRepository;
import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApplicationDraftServiceTest {

    @Autowired
    private ApplicationDraftService applicationDraftService;

    @Autowired
    private ApplicationDraftRepository applicationDraftRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("사용자의 임시저장이 없으면 새로 생성한다")
    void saveOrUpdateCreatesDraft() {
        User user = saveUser("draft-create@example.com");
        ApplicationDraftUpsertRequest request = new ApplicationDraftUpsertRequest(
                ApplicationDraftStep.COMPANY_SELECT,
                ApplyType.ACTUAL,
                1001L,
                null,
                null,
                List.of(1L, 2L)
        );

        ApplicationDraftSaveResponse response = applicationDraftService.saveOrUpdate(user, request);
        ApplicationDraftResponse draft = applicationDraftService.getMyDraft(user);

        assertThat(response.draftId()).isNotNull();
        assertThat(response.step()).isEqualTo(ApplicationDraftStep.COMPANY_SELECT);
        assertThat(response.savedAt()).isNotNull();
        assertThat(draft.type()).isEqualTo(ApplyType.ACTUAL);
        assertThat(draft.postingId()).isEqualTo(1001L);
        assertThat(draft.selectedQuestionIds()).containsExactly(1L, 2L);
        assertThat(applicationDraftRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("사용자의 임시저장이 이미 있으면 기존 데이터 1건을 수정한다")
    void saveOrUpdateUpdatesExistingDraft() {
        User user = saveUser("draft-update@example.com");
        applicationDraftService.saveOrUpdate(user, new ApplicationDraftUpsertRequest(
                ApplicationDraftStep.COMPANY_SELECT,
                ApplyType.ACTUAL,
                1001L,
                null,
                null,
                List.of(1L)
        ));

        ApplicationDraftSaveResponse updated = applicationDraftService.saveOrUpdate(user, new ApplicationDraftUpsertRequest(
                ApplicationDraftStep.JOB_SELECT,
                ApplyType.MOCK,
                null,
                10L,
                100L,
                null
        ));
        ApplicationDraftResponse draft = applicationDraftService.getMyDraft(user);

        assertThat(applicationDraftRepository.count()).isEqualTo(1);
        assertThat(updated.step()).isEqualTo(ApplicationDraftStep.JOB_SELECT);
        assertThat(draft.type()).isEqualTo(ApplyType.MOCK);
        assertThat(draft.postingId()).isNull();
        assertThat(draft.middleCategoryId()).isEqualTo(10L);
        assertThat(draft.smallCategoryId()).isEqualTo(100L);
        assertThat(draft.selectedQuestionIds()).isEmpty();
    }

    @Test
    @DisplayName("임시저장이 없어도 삭제는 성공하고 조회 결과는 null이다")
    void deleteIsIdempotent() {
        User user = saveUser("draft-delete@example.com");

        applicationDraftService.deleteMyDraft(user);

        assertThat(applicationDraftService.getMyDraft(user)).isNull();
        assertThat(applicationDraftRepository.count()).isZero();
    }

    private User saveUser(String email) {
        return userRepository.save(User.signup("테스트 사용자", email, "encoded-password"));
    }
}
