package com.jobdri.jobdri_api.domain.skill.repository;

import com.jobdri.jobdri_api.domain.skill.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    List<Skill> findAllByExperienceId(Long experienceId);
    Optional<Skill> findByExperienceIdAndName(Long experienceId, String name);
}
