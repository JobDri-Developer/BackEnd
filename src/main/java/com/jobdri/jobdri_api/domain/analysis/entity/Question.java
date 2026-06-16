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
@Table(name = "questions")
public class Question extends BaseEntity {

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

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Builder.Default
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuestionAnalysis> questionAnalyses = new ArrayList<>();

    public static Question create(MockApply mockApply, String content, int limit, String answer) {
        return Question.builder()
                .mockApply(mockApply)
                .content(content)
                .limit(limit)
                .answer(answer)
                .build();
    }

    public void updateAnswer(String answer) {
        this.answer = answer;
    }
}
