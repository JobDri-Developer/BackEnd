package com.jobdri.jobdri_api.domain.mockapply.entity;

import com.jobdri.jobdri_api.global.entity.BaseEntity;
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
                columnNames = {"user_id", "job_posting_id"}
        )
)
public class MockApplySequence extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_posting_id", nullable = false)
    private Long jobPostingId;

    @Column(nullable = false)
    private int lastSequence;

    public static MockApplySequence create(
            Long userId,
            Long jobPostingId,
            int lastSequence
    ) {
        return MockApplySequence.builder()
                .userId(userId)
                .jobPostingId(jobPostingId)
                .lastSequence(lastSequence)
                .build();
    }

    public int incrementAndGet() {
        lastSequence++;
        return lastSequence;
    }
}
