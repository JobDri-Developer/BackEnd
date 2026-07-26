package com.jobdri.jobdri_api.domain.mockapply.entity;

import com.jobdri.jobdri_api.domain.analysis.entity.Analysis;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.user.entity.User;
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
@Table(
        name = "mock_applies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mock_apply_user_posting_sequence",
                columnNames = {"user_id", "job_posting_id", "sequence"}
        )
)
public class MockApply extends BaseEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MockApplyStatus status;

    private Integer sequence;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @OneToOne(mappedBy = "mockApply", cascade = CascadeType.ALL, orphanRemoval = true)
    private Analysis analysis;

    @Builder.Default
    @OneToMany(mappedBy = "mockApply", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Question> questions = new ArrayList<>();

    public static MockApply create(User user, JobPosting jobPosting, ApplyType applyType) {
        return create(user, jobPosting, applyType, null);
    }

    public static MockApply create(User user, JobPosting jobPosting, ApplyType applyType, Integer sequence) {
        return MockApply.builder()
                .user(user)
                .jobPosting(jobPosting)
                .applyType(applyType)
                .status(MockApplyStatus.APPLICATION_CREATED)
                .sequence(sequence)
                .build();
    }

    public void updateStatus(MockApplyStatus status) {
        this.status = status;
    }

    public void updateDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Question addQuestion(String content, int limit, String answer) {
        Question question = Question.create(this, content, limit, answer);
        this.questions.add(question);
        return question;
    }

    public void assignAnalysis(Analysis analysis) {
        this.analysis = analysis;
    }

    public void clearAnalysis() {
        this.analysis = null;
    }
}
