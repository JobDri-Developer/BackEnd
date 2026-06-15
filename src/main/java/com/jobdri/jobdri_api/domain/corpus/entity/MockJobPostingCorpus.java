package com.jobdri.jobdri_api.domain.corpus.entity;

import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "mock_job_posting_corpus",
        indexes = {
                @Index(name = "idx_mock_job_posting_corpus_source_analysis", columnList = "source_analysis_id"),
                @Index(name = "idx_mock_job_posting_corpus_company", columnList = "company_id"),
                @Index(name = "idx_mock_job_posting_corpus_classification", columnList = "job_group_l1, job_family_l2, role_l3")
        }
)
public class MockJobPostingCorpus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_analysis_id", nullable = false, length = 100, unique = true)
    private String sourceAnalysisId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detail_classification_id")
    private DetailClassification detailClassification;

    @Column(name = "company_name", columnDefinition = "TEXT")
    private String companyName;

    @Column(columnDefinition = "TEXT")
    private String industry;

    @Column(name = "job_group_l1", columnDefinition = "TEXT")
    private String jobGroupL1;

    @Column(name = "job_family_l2", columnDefinition = "TEXT")
    private String jobFamilyL2;

    @Column(name = "role_l3", columnDefinition = "TEXT")
    private String roleL3;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(name = "responsibilities", columnDefinition = "TEXT")
    private String responsibilities;

    @Column(name = "requirements", columnDefinition = "TEXT")
    private String requirements;

    @Column(name = "preferred", columnDefinition = "TEXT")
    private String preferred;

    @Column(name = "embedding_text", nullable = false, columnDefinition = "TEXT")
    private String embeddingText;

    @Column(name = "is_valid_for_embedding", nullable = false)
    private boolean validForEmbedding;

    @Column(name = "invalid_reason", columnDefinition = "TEXT")
    private String invalidReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static MockJobPostingCorpus create(
            String sourceAnalysisId,
            Company company,
            DetailClassification detailClassification,
            String companyName,
            String industry,
            String jobGroupL1,
            String jobFamilyL2,
            String roleL3,
            String skills,
            String responsibilities,
            String requirements,
            String preferred,
            String embeddingText,
            boolean validForEmbedding,
            String invalidReason
    ) {
        return MockJobPostingCorpus.builder()
                .sourceAnalysisId(sourceAnalysisId)
                .company(company)
                .detailClassification(detailClassification)
                .companyName(companyName)
                .industry(industry)
                .jobGroupL1(jobGroupL1)
                .jobFamilyL2(jobFamilyL2)
                .roleL3(roleL3)
                .skills(skills)
                .responsibilities(responsibilities)
                .requirements(requirements)
                .preferred(preferred)
                .embeddingText(embeddingText)
                .validForEmbedding(validForEmbedding)
                .invalidReason(invalidReason)
                .createdAt(LocalDateTime.now())
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
            String industry,
            String jobGroupL1,
            String jobFamilyL2,
            String roleL3,
            String skills,
            String responsibilities,
            String requirements,
            String preferred,
            String embeddingText,
            boolean validForEmbedding,
            String invalidReason
    ) {
        this.company = company;
        this.detailClassification = detailClassification;
        this.companyName = companyName;
        this.industry = industry;
        this.jobGroupL1 = jobGroupL1;
        this.jobFamilyL2 = jobFamilyL2;
        this.roleL3 = roleL3;
        this.skills = skills;
        this.responsibilities = responsibilities;
        this.requirements = requirements;
        this.preferred = preferred;
        this.embeddingText = embeddingText;
        this.validForEmbedding = validForEmbedding;
        this.invalidReason = invalidReason;
    }
}
