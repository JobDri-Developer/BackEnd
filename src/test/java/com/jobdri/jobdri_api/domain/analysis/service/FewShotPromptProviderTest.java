package com.jobdri.jobdri_api.domain.analysis.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FewShotPromptProviderTest {
    private static final Pattern EXAMPLE_HEADER_PATTERN = Pattern.compile("(?m)^## 예시 ");

    @Test
    @DisplayName("few-shot 리소스를 UTF-8로 읽고 v4 예시 구성을 제공한다")
    void readsFewShotPromptResource() {
        FewShotPromptProvider provider = new FewShotPromptProvider();

        String prompt = provider.getPrompt();

        assertThat(prompt).isNotBlank();
        assertThat(EXAMPLE_HEADER_PATTERN.matcher(prompt).results().count()).isEqualTo(4);
        assertThat(prompt)
                .contains("PROVEN")
                .contains("\"status\": \"mentioned\"")
                .contains("\"status\": \"fabricated\"")
                .contains("\"missingKeywords\"")
                .contains("\"questionAnalyses\": []")
                .contains("예시의 분석 개수, 상태 비율, 문장 표현, 점수를 실제 입력에 복사하지 않는다.");
        assertThat(prompt).doesNotContain("\"jobFit\"");
        assertThat(prompt).doesNotContain("\"impact\"");
        assertThat(prompt).doesNotContain("\"completeness\"");
        assertThat(prompt).doesNotContain("weaknessType");
        assertThat(prompt).doesNotContain("dimension");
        assertThat(prompt).doesNotContain("relatedRequirement");
    }
}
