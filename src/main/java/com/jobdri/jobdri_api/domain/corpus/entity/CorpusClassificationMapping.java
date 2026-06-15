package com.jobdri.jobdri_api.domain.corpus.entity;

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
        name = "corpus_classification_mappings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_corpus_classification_mapping_source_triplet",
                        columnNames = {"source_job_group_l1", "source_job_family_l2", "source_role_l3"}
                )
        }
)
public class CorpusClassificationMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_job_group_l1", nullable = false, columnDefinition = "TEXT")
    private String sourceJobGroupL1;

    @Column(name = "source_job_family_l2", nullable = false, columnDefinition = "TEXT")
    private String sourceJobFamilyL2;

    @Column(name = "source_role_l3", nullable = false, columnDefinition = "TEXT")
    private String sourceRoleL3;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detail_classification_id", nullable = false)
    private DetailClassification detailClassification;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static CorpusClassificationMapping create(
            String sourceJobGroupL1,
            String sourceJobFamilyL2,
            String sourceRoleL3,
            DetailClassification detailClassification
    ) {
        return CorpusClassificationMapping.builder()
                .sourceJobGroupL1(sourceJobGroupL1)
                .sourceJobFamilyL2(sourceJobFamilyL2)
                .sourceRoleL3(sourceRoleL3)
                .detailClassification(detailClassification)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
