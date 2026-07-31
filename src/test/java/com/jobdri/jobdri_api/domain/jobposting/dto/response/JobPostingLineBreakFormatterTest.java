package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingLineBreakFormatterTest {

    @Test
    @DisplayName("자격 요건과 우대 사항 응답은 마지막 줄 끝에도 줄바꿈 문자를 포함한다")
    void appendLineBreakAfterLastLine() {
        JobPostingGenerateResponse response = new JobPostingGenerateResponse(
                "백엔드 개발자 채용",
                "잡드리",
                "백엔드 개발자",
                "API 개발",
                "Spring Boot 경험\nJPA 사용 경험",
                "AWS 경험\r\nRedis 경험",
                "요약"
        );

        assertThat(response.requirements()).isEqualTo("Spring Boot 경험\nJPA 사용 경험\n");
        assertThat(response.preferredQualifications()).isEqualTo("AWS 경험\nRedis 경험\n");
    }

    @Test
    @DisplayName("이미 마지막 줄 끝에 줄바꿈 문자가 있으면 중복 추가하지 않는다")
    void appendLineBreakAfterLastLineKeepsExistingLineBreak() {
        assertThat(JobPostingLineBreakFormatter.appendLineBreakAfterLastLine("AWS 경험\n"))
                .isEqualTo("AWS 경험\n");
    }
}
