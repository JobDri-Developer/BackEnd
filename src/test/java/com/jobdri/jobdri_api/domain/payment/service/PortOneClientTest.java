package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortOneClientTest {

    @Mock(answer = Answers.RETURNS_SELF)
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    private PortOneClient portOneClient;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.build()).thenReturn(restClient);
        portOneClient = new PortOneClient(restClientBuilder);
        ReflectionTestUtils.setField(portOneClient, "apiBaseUrl", "https://api.portone.io");
    }

    @Test
    @DisplayName("PORTONE_ENABLED=false이면 포트원 키가 없어도 초기화된다")
    void initDoesNotFailWhenDisabledAndKeysMissing() {
        ReflectionTestUtils.setField(portOneClient, "enabled", false);
        ReflectionTestUtils.setField(portOneClient, "storeId", "");
        ReflectionTestUtils.setField(portOneClient, "channelKey", "");
        ReflectionTestUtils.setField(portOneClient, "apiSecret", "");
        ReflectionTestUtils.setField(portOneClient, "redirectUrl", "");

        assertThatCode(() -> portOneClient.init()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PORTONE_ENABLED=true이면 필수 설정 누락 시 초기화에 실패한다")
    void initFailsWhenEnabledAndRequiredConfigMissing() {
        ReflectionTestUtils.setField(portOneClient, "enabled", true);
        ReflectionTestUtils.setField(portOneClient, "storeId", "");
        ReflectionTestUtils.setField(portOneClient, "channelKey", "");
        ReflectionTestUtils.setField(portOneClient, "apiSecret", "");
        ReflectionTestUtils.setField(portOneClient, "redirectUrl", "");

        assertThatThrownBy(() -> portOneClient.init())
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);
    }
}
