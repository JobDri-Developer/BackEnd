package com.jobdri.jobdri_api.domain.analysis.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "question_analyses")
public class QuestionAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String sentence;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String improvement;

    @Column(nullable = false)
    private int start;

    @Column(nullable = false)
    private int end;

    public static QuestionAnalysis create(
            Question question,
            Analysis analysis,
            String sentence,
            String reason,
            String improvement,
            int start,
            int end
    ) {
        return QuestionAnalysis.builder()
                .question(question)
                .analysis(analysis)
                .sentence(sentence)
                .reason(reason)
                .improvement(improvement)
                .start(start)
                .end(end)
                .build();
    }
}
