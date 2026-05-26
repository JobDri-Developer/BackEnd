package com.jobdri.jobdri_api.domain.mockapply.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "mock_apply_sequences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mock_apply_sequences_key",
                columnNames = {"user_id", "company_id", "detail_classification_id"}
        )
)
public class MockApplySequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "detail_classification_id", nullable = false)
    private Long detailClassificationId;

    @Column(nullable = false)
    private int lastSequence;

    public static MockApplySequence create(
            Long userId,
            Long companyId,
            Long detailClassificationId,
            int lastSequence
    ) {
        return MockApplySequence.builder()
                .userId(userId)
                .companyId(companyId)
                .detailClassificationId(detailClassificationId)
                .lastSequence(lastSequence)
                .build();
    }

    public int incrementAndGet() {
        lastSequence++;
        return lastSequence;
    }
}
