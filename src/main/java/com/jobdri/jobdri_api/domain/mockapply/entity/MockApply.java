package com.jobdri.jobdri_api.domain.mockapply.entity;

import com.jobdri.jobdri_api.domain.analysis.entity.Analysis;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "mock_applies")
public class MockApply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplyType applyType;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "mockApply", cascade = CascadeType.ALL, orphanRemoval = true)
    private Analysis analysis;

    @Builder.Default
    @OneToMany(mappedBy = "mockApply", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Question> questions = new ArrayList<>();

    public static MockApply create(User user, JobPosting jobPosting, ApplyType applyType) {
        return MockApply.builder()
                .user(user)
                .jobPosting(jobPosting)
                .applyType(applyType)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public Question addQuestion(String content, int limit, String answer) {
        Question question = Question.create(this, content, limit, answer);
        this.questions.add(question);
        return question;
    }

    public void assignAnalysis(Analysis analysis) {
        this.analysis = analysis;
    }
}
