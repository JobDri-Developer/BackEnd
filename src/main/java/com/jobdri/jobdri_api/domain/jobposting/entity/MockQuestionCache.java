package com.jobdri.jobdri_api.domain.jobposting.entity;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "mock_question_caches",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mock_question_cache_detail_version",
                columnNames = {"detail_classification_id", "prompt_version"}
        )
)
public class MockQuestionCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detail_classification_id", nullable = false)
    private DetailClassification detailClassification;

    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;

    @Builder.Default
    @ElementCollection
    @CollectionTable(
            name = "mock_question_cache_items",
            joinColumns = @JoinColumn(name = "mock_question_cache_id")
    )
    @OrderColumn(name = "question_order")
    @Column(name = "question_content", nullable = false, columnDefinition = "TEXT")
    private List<String> questions = new ArrayList<>();

    public static MockQuestionCache create(
            DetailClassification detailClassification,
            String promptVersion,
            List<String> questions
    ) {
        return MockQuestionCache.builder()
                .detailClassification(detailClassification)
                .promptVersion(promptVersion)
                .questions(new ArrayList<>(questions))
                .build();
    }
}
