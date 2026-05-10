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
@Table(name = "classifications")
public class Classification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String bigName;

    @Builder.Default
    @OneToMany(mappedBy = "classification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MiddleClassification> middleClassifications = new ArrayList<>();

    public static Classification create(String bigName) {
        return Classification.builder()
                .bigName(bigName)
                .build();
    }

    public MiddleClassification addMiddleClassification(String middleName) {
        MiddleClassification middleClassification = MiddleClassification.create(this, middleName);
        this.middleClassifications.add(middleClassification);
        return middleClassification;
    }
}
