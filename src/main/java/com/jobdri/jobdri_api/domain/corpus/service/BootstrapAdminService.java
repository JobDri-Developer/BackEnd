package com.jobdri.jobdri_api.domain.corpus.service;

import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.entity.UserRole;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BootstrapAdminService {

    @Value("${app.admin.bootstrap-emails:}")
    private String bootstrapEmails;

    private final UserRepository userRepository;

    @Transactional
    public void promoteConfiguredAdmins() {
        List<String> emails = Arrays.stream(bootstrapEmails.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();

        for (String email : emails) {
            userRepository.findByEmail(email)
                    .ifPresentOrElse(
                            this::promoteIfNeeded,
                            () -> log.warn("bootstrap admin 대상 사용자를 찾지 못했습니다. email={}", email)
                    );
        }
    }

    private void promoteIfNeeded(User user) {
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }
        user.promoteToAdmin();
        log.info("관리자 권한을 부여했습니다. email={}", user.getEmail());
    }
}
