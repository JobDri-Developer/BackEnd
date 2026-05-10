package com.jobdri.jobdri_api.domain.user.entity;

import com.jobdri.jobdri_api.domain.experience.entity.Experience;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.payment.entity.Payment;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    @Email
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private SocialType socialType;

    @Column(unique = true)
    private String socialId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private int credit;

    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Experience> experiences = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MockApply> mockApplies = new ArrayList<>();

    public static User signup(
            String name,
            String email,
            String encodedPassword
    ) {
        return User.builder()
                .name(name)
                .email(email)
                .password(encodedPassword)
                .role(UserRole.USER)
                .socialType(SocialType.LOCAL)
                .socialId(null)
                .credit(0)
                .build();
    }

    public static User createSocialUser(
            String name,
            String email,
            String password,
            SocialType socialType,
            String socialId
    ) {
        return User.builder()
                .name(name)
                .email(email)
                .password(password)
                .socialType(socialType)
                .socialId(socialId)
                .role(UserRole.USER)
                .credit(0)
                .build();
    }

    public void increaseCredit(int amount) {
        this.credit += amount;
    }

    public void decreaseCredit(int amount) {
        if (this.credit < amount) {
            throw new IllegalArgumentException("크레딧이 부족합니다.");
        }
        this.credit -= amount;
    }
}
