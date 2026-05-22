package com.jobdri.jobdri_api.domain.analysis.entity;

import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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
public class CustomQuestionCandidate {

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

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static CustomQuestionCandidate create(MockApply mockApply, String content, int limit) {
        return CustomQuestionCandidate.builder()
                .mockApply(mockApply)
                .content(content)
                .limit(limit)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
