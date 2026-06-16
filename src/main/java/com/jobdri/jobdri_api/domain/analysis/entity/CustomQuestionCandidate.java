package com.jobdri.jobdri_api.domain.analysis.entity;

import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.global.entity.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "custom_question_candidates",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_custom_question_candidates_mock_apply_content",
                        columnNames = {"mock_apply_id", "content"}
                )
        }
)
public class CustomQuestionCandidate extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mock_apply_id", nullable = false)
    private MockApply mockApply;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "char_limit", nullable = false)
    private int limit;

    public static CustomQuestionCandidate create(MockApply mockApply, String content, int limit) {
        return CustomQuestionCandidate.builder()
                .mockApply(mockApply)
                .content(content)
                .limit(limit)
                .build();
    }
}
