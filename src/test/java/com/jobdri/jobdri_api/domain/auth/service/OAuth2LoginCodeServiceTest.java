package com.jobdri.jobdri_api.domain.auth.service;

import com.jobdri.jobdri_api.domain.auth.dto.response.LoginResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2LoginCodeServiceTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final OAuth2LoginCodeService service = new OAuth2LoginCodeService(redisTemplate);

    @Test
    @DisplayName("로그인 코드는 원문이 아닌 해시 키로 60초 동안 저장된다")
    void issueStoresTokensUnderHashedKeyWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String code = service.issue(new LoginResponse("access-token", "refresh-token"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                keyCaptor.capture(),
                eq("access-token\nrefresh-token"),
                eq(OAuth2LoginCodeService.LOGIN_CODE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
        assertThat(code).hasSizeGreaterThanOrEqualTo(43);
        assertThat(keyCaptor.getValue())
                .startsWith(OAuth2LoginCodeService.LOGIN_CODE_PREFIX)
                .doesNotContain(code);
    }

    @Test
    @DisplayName("발급한 코드는 같은 키에서 꺼내 삭제하며 토큰으로 교환된다")
    void exchangeReturnsTokensFromIssuedCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String code = service.issue(new LoginResponse("access-token", "refresh-token"));
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), anyString(), eq(60L), eq(TimeUnit.SECONDS));
        when(valueOperations.getAndDelete(keyCaptor.getValue())).thenReturn("access-token\nrefresh-token");

        LoginResponse response = service.exchange(code);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("만료되었거나 이미 사용된 코드는 INVALID_TOKEN으로 거부한다")
    void exchangeRejectsUnknownCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.exchange("used-code"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("빈 코드는 Redis 조회 없이 거부한다")
    void exchangeRejectsBlankCodeWithoutRedisLookup() {
        assertThatThrownBy(() -> service.exchange(" "))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_TOKEN);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("저장값 형식이 깨져 있으면 토큰을 반환하지 않는다")
    void exchangeRejectsMalformedStoredValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(anyString())).thenReturn("only-access-token");

        assertThatThrownBy(() -> service.exchange("some-code"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_TOKEN);
    }
}
