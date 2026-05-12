package com.jobdri.jobdri_api.domain.applicationdraft.repository;

import com.jobdri.jobdri_api.domain.applicationdraft.entity.ApplicationDraft;
import com.jobdri.jobdri_api.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicationDraftRepository extends JpaRepository<ApplicationDraft, Long> {
    Optional<ApplicationDraft> findByUser(User user);

    void deleteByUser(User user);
}
