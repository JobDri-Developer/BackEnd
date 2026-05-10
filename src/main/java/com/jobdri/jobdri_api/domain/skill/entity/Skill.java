package com.jobdri.jobdri_api.domain.skill.entity;

import com.jobdri.jobdri_api.domain.experience.entity.Experience;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "skills")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "experience_id", nullable = false)
    private Experience experience;

    @Column(nullable = false)
    private String name;

    public static Skill create(Experience experience, String name) {
        return Skill.builder()
                .experience(experience)
                .name(name)
                .build();
    }
}
