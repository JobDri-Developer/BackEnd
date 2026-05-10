package com.jobdri.jobdri_api.domain.classification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "middle_classifications")
public class MiddleClassification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classification_id", nullable = false)
    private Classification classification;

    @Column(nullable = false)
    private String middleName;

    @Builder.Default
    @OneToMany(mappedBy = "middleClassification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetailClassification> detailClassifications = new ArrayList<>();

    public static MiddleClassification create(Classification classification, String middleName) {
        return MiddleClassification.builder()
                .classification(classification)
                .middleName(middleName)
                .build();
    }

    public DetailClassification addDetailClassification(String detailName) {
        DetailClassification detailClassification = DetailClassification.create(this, detailName);
        this.detailClassifications.add(detailClassification);
        return detailClassification;
    }
}
