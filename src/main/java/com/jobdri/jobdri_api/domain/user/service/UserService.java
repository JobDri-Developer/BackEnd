package com.jobdri.jobdri_api.domain.user.service;

import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public User validateUser(User user) {
        if (user == null || user.getId() == null) {
            throw new GeneralException(GeneralErrorCode.MISSING_AUTH_INFO, "인증 정보가 누락되었습니다.");
        }

        return userRepository.findById(user.getId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.USER_NOT_FOUND,
                        "해당 유저를 찾을 수 없습니다. userId=" + user.getId()
                ));
    }
}
