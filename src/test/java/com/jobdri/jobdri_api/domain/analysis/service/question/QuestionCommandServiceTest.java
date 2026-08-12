package com.jobdri.jobdri_api.domain.analysis.service.question;

import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionCandidateCreateRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.CustomQuestionCandidate;
import com.jobdri.jobdri_api.domain.analysis.repository.CustomQuestionCandidateRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionAnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionCommandServiceTest {

    @Mock
    private QuestionDomainSupport questionDomainSupport;

    @Mock
    private QuestionCandidateCatalogService questionCandidateCatalogService;

    @Mock
    private MockApplyRepository mockApplyRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuestionAnalysisRepository questionAnalysisRepository;

    @Mock
    private CustomQuestionCandidateRepository customQuestionCandidateRepository;

    @Mock
    private CustomQuestionCandidatePersistenceService customQuestionCandidatePersistenceService;

    @Test
    @DisplayName("custom candidate 저장 충돌 시 기존 후보를 재조회해 정상 응답한다")
    void addCustomQuestionCandidateRecoversAfterUniqueConflict() {
        QuestionCommandService questionCommandService = new QuestionCommandService(
                questionDomainSupport,
                questionCandidateCatalogService,
                mockApplyRepository,
                questionRepository,
                questionAnalysisRepository,
                customQuestionCandidateRepository,
                customQuestionCandidatePersistenceService
        );
        User user = User.signup("테스트 사용자", "question-command@example.com", "encoded-password");
        MockApply mockApply = mock(MockApply.class);
        when(mockApply.getId()).thenReturn(10L);

        CustomQuestionCandidate existingCandidate = CustomQuestionCandidate.create(
                mockApply,
                "새로운 지원 동기를 작성해주세요.",
                900
        );
        ReflectionTestUtils.setField(existingCandidate, "id", 7L);

        when(questionDomainSupport.getOwnedMockApply(user, 10L)).thenReturn(mockApply);
        doNothing().when(questionCandidateCatalogService).validateCustomCandidate("새로운 지원 동기를 작성해주세요.");
        when(questionCandidateCatalogService.toCustomCandidateKey(7L)).thenReturn("custom:7");
        when(questionDomainSupport.resolveCharLimit(900)).thenReturn(900);
        when(customQuestionCandidateRepository.findByMockApplyIdAndContent(10L, "새로운 지원 동기를 작성해주세요."))
                .thenReturn(Optional.empty(), Optional.of(existingCandidate));
        when(customQuestionCandidatePersistenceService.saveAndFlush(10L, "새로운 지원 동기를 작성해주세요.", 900))
                .thenThrow(new DataIntegrityViolationException("duplicate candidate"));
        when(questionRepository.existsByMockApplyIdAndContent(10L, "새로운 지원 동기를 작성해주세요."))
                .thenReturn(false);

        QuestionCandidateResponse response = questionCommandService.addCustomQuestionCandidate(
                user,
                10L,
                new QuestionCandidateCreateRequest("  새로운 지원 동기를 작성해주세요.  ", 900)
        );

        assertThat(response.questionId()).isEqualTo(7L);
        assertThat(response.content()).isEqualTo("새로운 지원 동기를 작성해주세요.");
        assertThat(response.charLimit()).isEqualTo(900);
        assertThat(response.selected()).isFalse();
        assertThat(response.custom()).isTrue();
        assertThat(response.candidateKey()).isEqualTo("custom:7");
        verify(customQuestionCandidatePersistenceService).saveAndFlush(10L, "새로운 지원 동기를 작성해주세요.", 900);
        verify(customQuestionCandidateRepository, never()).saveAndFlush(existingCandidate);
    }
}
