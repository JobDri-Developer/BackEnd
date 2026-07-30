package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingProfileColor;
import com.jobdri.jobdri_api.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingEmbeddingTextBuilderTest {

    private final JobPostingEmbeddingTextBuilder textBuilder = new JobPostingEmbeddingTextBuilder();

    @Test
    @DisplayName("채용 공고 필드를 corpus와 같은 단일 embedding text로 구성한다")
    void build() {
        JobPosting jobPosting = jobPosting(
                "백엔드 개발자",
                "Spring Boot API 개발\nMSA\nRabbitMQ",
                "Java\nSpring\nJPA",
                "AWS\nDocker"
        );

        String text = textBuilder.build(jobPosting);

        assertThat(text).isEqualTo("""
                직무
                백엔드 개발자

                주요업무
                Spring Boot API 개발
                MSA
                RabbitMQ

                자격요건
                Java
                Spring
                JPA

                우대사항
                AWS
                Docker""");
    }

    @Test
    @DisplayName("blank 필드는 embedding text에서 제외한다")
    void skipBlankFields() {
        JobPosting jobPosting = jobPosting("백엔드 개발자", "Spring Boot API 개발", "Java", " ");

        String text = textBuilder.build(jobPosting);

        assertThat(text).doesNotContain("우대사항");
        assertThat(text).contains("직무\n백엔드 개발자", "주요업무\nSpring Boot API 개발", "자격요건\nJava");
    }

    private JobPosting jobPosting(String jobTitle, String task, String requirement, String preferred) {
        Classification classification = Classification.create("개발");
        DetailClassification detailClassification = classification
                .addMiddleClassification("서버")
                .addDetailClassification("백엔드");
        return JobPosting.create(
                User.authenticatedPrincipal(1L, "user@example.com", com.jobdri.jobdri_api.domain.user.entity.UserRole.USER),
                Company.create("테스트 기업", CompanySize.MEDIUM),
                detailClassification,
                JobPostingProfileColor.DEFAULT,
                "테스트 공고",
                jobTitle,
                task,
                requirement,
                preferred
        );
    }
}
