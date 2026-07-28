package com.jobdri.jobdri_api.domain.analysis.service.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisQueuePropertiesTest {

    @Test
    @DisplayName("자소서 분석 큐 timeout 기본값은 초 단위 설정을 사용한다")
    void defaultTimeoutsUseSeconds() {
        AnalysisQueueProperties properties = new AnalysisQueueProperties();

        assertThat(properties.getQueueTimeoutSeconds()).isEqualTo(120L);
        assertThat(properties.getProcessingTimeoutSeconds()).isEqualTo(600L);
    }

    @Test
    @DisplayName("초 단위 timeout이 비어 있으면 기존 분 단위 설정으로 fallback한다")
    void timeoutFallsBackToMinutesWhenSecondsIsMissing() {
        AnalysisQueueProperties properties = new AnalysisQueueProperties();
        properties.setQueueTimeoutSeconds(null);
        properties.setProcessingTimeoutSeconds(0L);
        properties.setQueueTimeoutMinutes(2L);
        properties.setProcessingTimeoutMinutes(3L);

        assertThat(properties.getQueueTimeoutSeconds()).isEqualTo(120L);
        assertThat(properties.getProcessingTimeoutSeconds()).isEqualTo(180L);
    }
}
