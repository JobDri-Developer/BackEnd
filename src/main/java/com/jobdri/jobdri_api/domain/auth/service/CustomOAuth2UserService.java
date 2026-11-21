package com.jobdri.jobdri_api.domain.auth.service;

import com.jobdri.jobdri_api.domain.auth.security.CustomOAuth2User;
import com.jobdri.jobdri_api.domain.user.entity.SocialType;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final String encodedDummyPassword;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    @Autowired
    public CustomOAuth2UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this(userRepository, passwordEncoder, new DefaultOAuth2UserService());
    }

    CustomOAuth2UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate
    ) {
        this.userRepository = userRepository;
        this.encodedDummyPassword = passwordEncoder.encode("SOCIAL_LOGIN_ONLY_ACCOUNT");
        this.delegate = delegate;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email");
        String name = (String) attributes.getOrDefault("name", email);
        String socialId = (String) attributes.get("sub");

        if (email == null || socialId == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info"),
                    "구글 사용자 정보 조회에 실패했습니다."
            );
        }

        // 같은 이메일의 기존 계정과 연결하므로, Google이 소유를 확인한 이메일만 받는다.
        if (!isEmailVerified(attributes.get("email_verified"))) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unverified_email"),
                    "구글 계정의 이메일 인증이 완료되지 않았습니다."
            );
        }

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(
                        User.createSocialUser(
                                name,
                                email,
                                encodedDummyPassword,
                                SocialType.GOOGLE,
                                socialId
                        )
                ));

        return new CustomOAuth2User(
                user,
                attributes,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    private boolean isEmailVerified(Object emailVerified) {
        if (emailVerified instanceof Boolean verified) {
            return verified;
        }
        return emailVerified instanceof String verified && Boolean.parseBoolean(verified);
    }
}
