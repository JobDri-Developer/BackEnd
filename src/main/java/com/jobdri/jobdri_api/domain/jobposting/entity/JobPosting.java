package com.jobdri.jobdri_api.domain.jobposting.entity;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "job_postings")
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detail_classification_id", nullable = false)
    private DetailClassification detailClassification;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String task;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String requirement;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String preferred;

    @Builder.Default
    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MockApply> mockApplies = new ArrayList<>();

    public static JobPosting create(
            Company company,
            DetailClassification detailClassification,
            String task,
            String requirement,
            String preferred
    ) {
        return JobPosting.builder()
                .company(company)
                .detailClassification(detailClassification)
                .task(task)
                .requirement(requirement)
                .preferred(preferred)
                .build();
    }
}
