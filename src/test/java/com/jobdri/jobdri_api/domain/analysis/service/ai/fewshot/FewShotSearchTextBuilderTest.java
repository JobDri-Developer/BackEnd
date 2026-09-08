package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FewShotSearchTextBuilderTest {
    private final FewShotSearchTextBuilder builder = new FewShotSearchTextBuilder();

    @Test
    @DisplayName("검색 질의는 직무, JD, 문항, 답변을 분리된 섹션으로 구성한다")
    void buildsQueryTextWithSeparatedSections() {
        String text = builder.buildQueryText(new FewShotSearchQuery(
                "EV-01",
                "백엔드 개발",
                "Backend Engineer",
                List.of("Spring Boot 기반 REST API 개발", "RDBMS 성능 개선"),
                List.of("Java 개발 경험"),
                "지원 직무 경험을 작성해 주세요.",
                "Spring Boot와 JPA로 API를 개발했습니다."
        ));

        assertThat(text)
                .contains("[JOB_CATEGORY]\n백엔드 개발")
                .contains("[JOB_TITLE]\nBackend Engineer")
                .contains("[MAIN_TASKS]\n- Spring Boot 기반 REST API 개발\n- RDBMS 성능 개선")
                .contains("[QUALIFICATIONS]\n- Java 개발 경험")
                .contains("[QUESTION]\n지원 직무 경험을 작성해 주세요.")
                .contains("[ANSWER]\nSpring Boot와 JPA로 API를 개발했습니다.");
    }

    @Test
    @DisplayName("후보 문서에는 원천과 후보 메타데이터를 포함하고 벡터 값은 포함하지 않는다")
    void buildsCandidateDocument() {
        FewShotCase fewShotCase = new FewShotCase(
                "FS-001",
                FewShotSource.REVIEWED_EVALUATION,
                FewShotReviewStatus.APPROVED,
                true,
                10,
                "백엔드 개발",
                "Backend Engineer",
                List.of("REST API 개발"),
                List.of("Spring Boot 경험"),
                "직무 경험",
                "API를 개발했습니다.",
                "{\"questionAnalyses\":[]}",
                List.of("spring", "api"),
                "fewshot-test-v1",
                "## 예시"
        );

        String document = builder.buildCandidateDocument(fewShotCase);

        assertThat(document)
                .contains("[CASE_ID]\nFS-001")
                .contains("[SOURCE]\nREVIEWED_EVALUATION")
                .contains("REST API 개발")
                .contains("Spring Boot 경험")
                .doesNotContain("embedding");
    }

    @Test
    @DisplayName("HTML을 제거하고 반복 공백을 정규화한 뒤 검색 텍스트를 구성한다")
    void normalizesHtmlAndWhitespace() {
        String text = builder.buildQueryText(new FewShotSearchQuery(
                "EV-01",
                "  백엔드   개발 ",
                "<b>Backend</b>&nbsp;Engineer",
                List.of("<p>Spring Boot</p>   API 개발"),
                List.of("Java\n\t개발 경험"),
                "지원 <strong>직무</strong> 경험",
                "JPA로   API를 <em>개발</em>했습니다.<script>ignore()</script>"
        ));

        assertThat(text)
                .contains("[JOB_CATEGORY]\n백엔드 개발")
                .contains("[JOB_TITLE]\nBackend Engineer")
                .contains("[MAIN_TASKS]\n- Spring Boot API 개발")
                .contains("[QUALIFICATIONS]\n- Java 개발 경험")
                .contains("[QUESTION]\n지원 직무 경험")
                .contains("[ANSWER]\nJPA로 API를 개발했습니다.")
                .doesNotContain("<b>", "<script>", "ignore()", "&nbsp;");
    }

    @Test
    @DisplayName("HTML 제거와 공백 정규화 후 답변 섹션 길이를 제한한다")
    void truncatesAnswerAfterNormalization() {
        String answer = "<b>가</b>   ".repeat(FewShotSearchTextBuilder.MAX_ANSWER_LENGTH + 100);

        String text = builder.buildQueryText(new FewShotSearchQuery(
                "EV-01",
                "백엔드 개발",
                "Backend Engineer",
                List.of(),
                List.of(),
                "직무 경험",
                answer
        ));
        String answerSection = text.substring(text.indexOf("[ANSWER]\n") + "[ANSWER]\n".length()).trim();

        assertThat(answerSection)
                .doesNotContain("<b>", "  ");
        assertThat(answerSection.length())
                .isPositive()
                .isLessThanOrEqualTo(FewShotSearchTextBuilder.MAX_ANSWER_LENGTH);
    }
}
