package com.jobdri.jobdri_api.domain.auth.entity;

import com.jobdri.jobdri_api.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "refresh_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_refresh_token_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_refresh_token_value", columnNames = "token")
        }
)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, unique = true, length = 1000)
    private String token;

    @Version
    private Long version;

    public static RefreshToken create(User user, String token) {
        return RefreshToken.builder()
                .user(user)
                .token(token)
                .build();
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }

    public void rotate(String newToken) {
        this.token = newToken;
    }
}
