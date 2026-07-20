package com.jobdri.jobdri_api.domain.analysis.entity;

import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "analyses")
public class Analysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mock_apply_id", nullable = false, unique = true)
    private MockApply mockApply;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private int jobFit;

    @Column(nullable = false)
    private int impact;

    @Column(nullable = false)
    private int completeness;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String feedback;

    @Builder.Default
    @Column(name = "missing_keywords", nullable = false, columnDefinition = "TEXT DEFAULT '[]'")
    private String missingKeywordsJson = "[]";

    @Builder.Default
    @Column(name = "key_strengths", nullable = false, columnDefinition = "TEXT DEFAULT '[]'")
    private String keyStrengthsJson = "[]";

    @Builder.Default
    @Column(name = "key_weaknesses", nullable = false, columnDefinition = "TEXT DEFAULT '[]'")
    private String keyWeaknessesJson = "[]";

    @Builder.Default
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuestionAnalysis> questionAnalyses = new ArrayList<>();

    public static Analysis create(
            MockApply mockApply,
            int score,
            int jobFit,
            int impact,
            int completeness,
            String feedback
    ) {
        return create(mockApply, score, jobFit, impact, completeness, feedback, "[]");
    }

    public static Analysis create(
            MockApply mockApply,
            int score,
            int jobFit,
            int impact,
            int completeness,
            String feedback,
            String missingKeywordsJson
    ) {
        return create(mockApply, score, jobFit, impact, completeness, feedback, missingKeywordsJson, "[]", "[]");
    }

    public static Analysis create(
            MockApply mockApply,
            int score,
            int jobFit,
            int impact,
            int completeness,
            String feedback,
            String missingKeywordsJson,
            String keyStrengthsJson,
            String keyWeaknessesJson
    ) {
        return Analysis.builder()
                .mockApply(mockApply)
                .score(score)
                .jobFit(jobFit)
                .impact(impact)
                .completeness(completeness)
                .feedback(feedback)
                .missingKeywordsJson(missingKeywordsJson == null ? "[]" : missingKeywordsJson)
                .keyStrengthsJson(keyStrengthsJson == null ? "[]" : keyStrengthsJson)
                .keyWeaknessesJson(keyWeaknessesJson == null ? "[]" : keyWeaknessesJson)
                .build();
    }
}
