package com.jobdri.jobdri_api.domain.classification.entity;

import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "detail_classifications")
public class DetailClassification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "middle_classification_id", nullable = false)
    private MiddleClassification middleClassification;

    @Column(nullable = false)
    private String detailName;

    @Builder.Default
    @OneToMany(mappedBy = "detailClassification")
    private List<JobPosting> jobPostings = new ArrayList<>();

    public static DetailClassification create(MiddleClassification middleClassification, String detailName) {
        return DetailClassification.builder()
                .middleClassification(middleClassification)
                .detailName(detailName)
                .build();
    }
}
