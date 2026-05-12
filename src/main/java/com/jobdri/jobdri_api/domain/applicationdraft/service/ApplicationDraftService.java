package com.jobdri.jobdri_api.domain.applicationdraft.service;

import com.jobdri.jobdri_api.domain.applicationdraft.dto.request.ApplicationDraftUpsertRequest;
import com.jobdri.jobdri_api.domain.applicationdraft.dto.response.ApplicationDraftResponse;
import com.jobdri.jobdri_api.domain.applicationdraft.dto.response.ApplicationDraftSaveResponse;
import com.jobdri.jobdri_api.domain.applicationdraft.entity.ApplicationDraft;
import com.jobdri.jobdri_api.domain.applicationdraft.repository.ApplicationDraftRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class ApplicationDraftService {

    private final ApplicationDraftRepository applicationDraftRepository;

    @Transactional
    public ApplicationDraftSaveResponse saveOrUpdate(User user, ApplicationDraftUpsertRequest request) {
        ApplicationDraft draft = applicationDraftRepository.findByUser(user)
                .map(existingDraft -> {
                    existingDraft.update(
                            request.step(),
                            request.type(),
                            request.postingId(),
                            request.middleCategoryId(),
                            request.smallCategoryId(),
                            request.selectedQuestionIds()
                    );
                    return existingDraft;
                })
                .orElseGet(() -> applicationDraftRepository.save(ApplicationDraft.create(
                        user,
                        request.step(),
                        request.type(),
                        request.postingId(),
                        request.middleCategoryId(),
                        request.smallCategoryId(),
                        request.selectedQuestionIds()
                )));

        return new ApplicationDraftSaveResponse(
                draft.getId(),
                draft.getStep(),
                draft.getSavedAt()
        );
    }

    @Transactional(readOnly = true)
    public ApplicationDraftResponse getMyDraft(User user) {
        return applicationDraftRepository.findByUser(user)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    public void deleteMyDraft(User user) {
        applicationDraftRepository.deleteByUser(user);
    }

    private ApplicationDraftResponse toResponse(ApplicationDraft draft) {
        return new ApplicationDraftResponse(
                draft.getId(),
                draft.getStep(),
                draft.getType(),
                draft.getPostingId(),
                draft.getMiddleCategoryId(),
                draft.getSmallCategoryId(),
                new ArrayList<>(draft.getSelectedQuestionIds()),
                draft.getSavedAt()
        );
    }
}
