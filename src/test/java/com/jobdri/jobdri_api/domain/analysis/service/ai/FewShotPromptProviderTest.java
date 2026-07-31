package com.jobdri.jobdri_api.domain.analysis.service.ai;

import com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot.FewShotCase;
import com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot.FewShotReviewStatus;
import com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot.FewShotSource;
import com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot.SelectedFewShotCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
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
                .contains("\"keyStrengths\"")
                .contains("\"status\": \"proven\"")
                .contains("\"status\": \"mentioned\"")
                .contains("\"status\": \"fabricated\"")
                .contains("\"missingKeywords\"")
                .contains("문항을 대표하는 긍정 근거이므로 questionAnalyses에는 proven으로 포함한다.")
                .contains("예시의 분석 개수, 상태 비율, 문장 표현, 점수를 실제 입력에 복사하지 않는다.");
        assertThat(prompt).doesNotContain("\"jobFit\"");
        assertThat(prompt).doesNotContain("\"impact\"");
        assertThat(prompt).doesNotContain("\"completeness\"");
        assertThat(prompt).doesNotContain("weaknessType");
        assertThat(prompt).doesNotContain("dimension");
        assertThat(prompt).doesNotContain("relatedRequirement");
    }

    @Test
    @DisplayName("선택된 few-shot이 있으면 해당 예시만 기존 형식으로 조립한다")
    void buildsPromptBlockFromSelectedFewShots() {
        FewShotPromptProvider provider = new FewShotPromptProvider();
        FewShotCase selectedCase = new FewShotCase(
                "FS-TEST",
                FewShotSource.CURATED,
                FewShotReviewStatus.APPROVED,
                true,
                0,
                "백엔드 개발",
                "Backend Engineer",
                List.of("API 개발"),
                List.of("Spring Boot"),
                "직무 경험",
                "API를 개발했습니다.",
                "{\"questionAnalyses\":[{\"questionId\":1,\"sentence\":\"API를 개발했습니다.\",\"status\":\"proven\",\"reason\":\"API 개발 경험이 구체적으로 드러납니다.\",\"improvement\":null}]}",
                List.of("api"),
                "fewshot-test-v1",
                "## 예시 Z: 선택 예시\n출력 중 문장/누락 관련 필드:\n{\"questionAnalyses\":[{\"questionId\":1,\"sentence\":\"API를 개발했습니다.\",\"status\":\"proven\",\"reason\":\"API 개발 경험이 구체적으로 드러납니다.\",\"improvement\":null}]}"
        );

        String prompt = provider.buildPromptBlock(List.of(new SelectedFewShotCase(selectedCase, 0.9, "test")));

        assertThat(prompt)
                .contains("[# Few-shot examples]")
                .contains("## 예시 Z: 선택 예시")
                .contains("\"sentence\":\"API를 개발했습니다.\"")
                .contains("\"status\":\"proven\"")
                .doesNotContain("## 예시 A:")
                .doesNotContain("## 예시 B:");
    }

    @Test
    @DisplayName("선택된 few-shot이 없으면 기존 전체 블록으로 fallback한다")
    void fallsBackToFixedPromptWhenSelectedFewShotsEmpty() {
        FewShotPromptProvider provider = new FewShotPromptProvider();

        assertThat(provider.buildPromptBlock(List.of())).isEqualTo(provider.getPrompt());
    }
}
