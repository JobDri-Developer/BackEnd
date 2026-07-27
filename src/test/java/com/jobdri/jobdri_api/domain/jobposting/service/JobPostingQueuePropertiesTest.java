package com.jobdri.jobdri_api.domain.jobposting.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingQueuePropertiesTest {

    @Test
    @DisplayName("JD 분석 큐 timeout 기본값은 초 단위 설정을 사용한다")
    void defaultTimeoutsUseSeconds() {
        JobPostingQueueProperties properties = new JobPostingQueueProperties();

        assertThat(properties.getQueueTimeoutSeconds()).isEqualTo(30L);
        assertThat(properties.getProcessingTimeoutSeconds()).isEqualTo(60L);
    }

    @Test
    @DisplayName("초 단위 timeout이 비어 있으면 기존 분 단위 설정으로 fallback한다")
    void timeoutFallsBackToMinutesWhenSecondsIsMissing() {
        JobPostingQueueProperties properties = new JobPostingQueueProperties();
        properties.setQueueTimeoutSeconds(null);
        properties.setProcessingTimeoutSeconds(0L);
        properties.setQueueTimeoutMinutes(2L);
        properties.setProcessingTimeoutMinutes(3L);

        assertThat(properties.getQueueTimeoutSeconds()).isEqualTo(120L);
        assertThat(properties.getProcessingTimeoutSeconds()).isEqualTo(180L);
    }
}
