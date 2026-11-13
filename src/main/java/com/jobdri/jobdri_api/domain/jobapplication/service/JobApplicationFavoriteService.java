package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationFavoriteResponse;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobApplicationFavoriteService {
    private final UserService userService;
    private final UserRepository users;
    private final JobApplicationRepository applications;

    @Transactional
    public JobApplicationFavoriteResponse set(User principal, Long id, boolean favorite) {
        User user = userService.validateUser(principal);
        users.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        var card = applications.findByIdForUpdate(id)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.JOB_APPLICATION_NOT_FOUND));
        if (!card.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 지원 카드에 접근할 수 없습니다.");
        }
        card.setFavorite(favorite);
        return new JobApplicationFavoriteResponse(id, card.isFavorite());
    }
}
