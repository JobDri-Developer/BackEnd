package com.jobdri.jobdri_api.domain.experience.entity;

import com.jobdri.jobdri_api.domain.skill.entity.Skill;
import com.jobdri.jobdri_api.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "experiences")
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExperienceCategory category;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private LocalDate start;

    private LocalDate end;

    @Column(nullable = false)
    private String detail1;

    private String detail2;

    private String detail3;

    @Builder.Default
    @OneToMany(mappedBy = "experience", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Skill> skills = new ArrayList<>();

    public static Experience create(
            User user,
            ExperienceCategory category,
            String name,
            String role,
            LocalDate start,
            LocalDate end,
            String detail1,
            String detail2,
            String detail3
    ) {
        return Experience.builder()
                .user(user)
                .category(category)
                .name(name)
                .role(role)
                .start(start)
                .end(end)
                .detail1(detail1)
                .detail2(detail2)
                .detail3(detail3)
                .build();
    }

    public Skill addSkill(String name) {
        Skill skill = Skill.create(this, name);
        this.skills.add(skill);
        return skill;
    }
}
