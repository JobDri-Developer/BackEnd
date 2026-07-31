package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingLineBreakFormatterTest {

    @Test
    @DisplayName("자격 요건과 우대 사항 응답은 항목마다 줄바꿈 문자를 포함한다")
    void appendLineBreakAfterLastLine() {
        JobPostingGenerateResponse response = new JobPostingGenerateResponse(
                "백엔드 개발자 채용",
                "잡드리",
                "백엔드 개발자",
                "API 개발",
                "Spring Boot 경험, JPA 사용 경험. AWS 경험.",
                "ROAS, CAC 지표 이해. Meta, Google 광고 운영 경험.",
                "요약"
        );

        assertThat(response.requirements()).isEqualTo("Spring Boot 경험, JPA 사용 경험.\nAWS 경험.\n");
        assertThat(response.preferredQualifications())
                .isEqualTo("ROAS, CAC 지표 이해.\nMeta, Google 광고 운영 경험.\n");
    }

    @Test
    @DisplayName("쉼표로 연결된 병렬 표현은 항목으로 분리하지 않는다")
    void appendLineBreakAfterLastLineKeepsCommaSeparatedPhrase() {
        assertThat(JobPostingLineBreakFormatter.appendLineBreakAfterLastLine("개발자, PM과 협업 경험. CRM 마케팅, 제휴 업무 경험."))
                .isEqualTo("개발자, PM과 협업 경험.\nCRM 마케팅, 제휴 업무 경험.\n");
    }

    @Test
    @DisplayName("불릿과 번호 항목도 각각 줄바꿈한다")
    void appendLineBreakAfterBulletAndNumberedItems() {
        assertThat(JobPostingLineBreakFormatter.appendLineBreakAfterLastLine("- Java 경험 - Spring 경험 1. AWS 경험 2. Redis 경험"))
                .isEqualTo("- Java 경험\n- Spring 경험\n1. AWS 경험\n2. Redis 경험\n");
    }

    @Test
    @DisplayName("이미 마지막 줄 끝에 줄바꿈 문자가 있으면 중복 추가하지 않는다")
    void appendLineBreakAfterLastLineKeepsExistingLineBreak() {
        assertThat(JobPostingLineBreakFormatter.appendLineBreakAfterLastLine("AWS 경험\n"))
                .isEqualTo("AWS 경험\n");
    }
}
