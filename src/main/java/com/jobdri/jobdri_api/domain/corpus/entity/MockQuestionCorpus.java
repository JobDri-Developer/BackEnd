package com.jobdri.jobdri_api.domain.corpus.entity;

import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "mock_question_corpus",
        indexes = {
                @Index(name = "idx_mock_question_corpus_source_question", columnList = "source_question_id"),
                @Index(name = "idx_mock_question_corpus_source_analysis", columnList = "source_analysis_id"),
                @Index(name = "idx_mock_question_corpus_company", columnList = "company_id"),
                @Index(name = "idx_mock_question_corpus_classification", columnList = "job_group_l1, job_family_l2, role_l3")
        }
)
public class MockQuestionCorpus extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_question_id", nullable = false, length = 120, unique = true)
    private String sourceQuestionId;

    @Column(name = "source_analysis_id", nullable = false, length = 100)
    private String sourceAnalysisId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detail_classification_id")
    private DetailClassification detailClassification;

    @Column(name = "company_name", columnDefinition = "TEXT")
    private String companyName;

    @Column(name = "job_group_l1", columnDefinition = "TEXT")
    private String jobGroupL1;

    @Column(name = "job_family_l2", columnDefinition = "TEXT")
    private String jobFamilyL2;

    @Column(name = "role_l3", columnDefinition = "TEXT")
    private String roleL3;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "question_type", columnDefinition = "TEXT")
    private String questionType;

    @Column(name = "char_limit")
    private Integer charLimit;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "embedding_text", nullable = false, columnDefinition = "TEXT")
    private String embeddingText;

    @Column(name = "is_valid_for_embedding", nullable = false)
    private boolean validForEmbedding;

    public static MockQuestionCorpus create(
            String sourceQuestionId,
            String sourceAnalysisId,
            Company company,
            DetailClassification detailClassification,
            String companyName,
            String jobGroupL1,
            String jobFamilyL2,
            String roleL3,
            String source,
            String questionType,
            Integer charLimit,
            String questionText,
            String embeddingText,
            boolean validForEmbedding
    ) {
        return MockQuestionCorpus.builder()
                .sourceQuestionId(sourceQuestionId)
                .sourceAnalysisId(sourceAnalysisId)
                .company(company)
                .detailClassification(detailClassification)
                .companyName(companyName)
                .jobGroupL1(jobGroupL1)
                .jobFamilyL2(jobFamilyL2)
                .roleL3(roleL3)
                .source(source)
                .questionType(questionType)
                .charLimit(charLimit)
                .questionText(questionText)
                .embeddingText(embeddingText)
                .validForEmbedding(validForEmbedding)
                .build();
    }

    public void assignCompany(Company company) {
        this.company = company;
    }

    public void assignDetailClassification(DetailClassification detailClassification) {
        this.detailClassification = detailClassification;
    }

    public void updateFromImport(
            Company company,
            DetailClassification detailClassification,
            String companyName,
            String jobGroupL1,
            String jobFamilyL2,
            String roleL3,
            String source,
            String questionType,
            Integer charLimit,
            String questionText,
            String embeddingText,
            boolean validForEmbedding
    ) {
        this.company = company;
        this.detailClassification = detailClassification;
        this.companyName = companyName;
        this.jobGroupL1 = jobGroupL1;
        this.jobFamilyL2 = jobFamilyL2;
        this.roleL3 = roleL3;
        this.source = source;
        this.questionType = questionType;
        this.charLimit = charLimit;
        this.questionText = questionText;
        this.embeddingText = embeddingText;
        this.validForEmbedding = validForEmbedding;
    }
}
