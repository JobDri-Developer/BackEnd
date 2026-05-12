package com.jobdri.jobdri_api.domain.applicationdraft.entity;

import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
import com.jobdri.jobdri_api.domain.user.entity.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "application_drafts")
public class ApplicationDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationDraftStep step;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplyType type;

    @Column(name = "posting_id")
    private Long postingId;

    @Column(name = "middle_category_id")
    private Long middleCategoryId;

    @Column(name = "small_category_id")
    private Long smallCategoryId;

    @Builder.Default
    @ElementCollection
    @CollectionTable(
            name = "application_draft_selected_questions",
            joinColumns = @JoinColumn(name = "application_draft_id")
    )
    @Column(name = "question_id", nullable = false)
    private List<Long> selectedQuestionIds = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static ApplicationDraft create(
            User user,
            ApplicationDraftStep step,
            ApplyType type,
            Long postingId,
            Long middleCategoryId,
            Long smallCategoryId,
            List<Long> selectedQuestionIds
    ) {
        return ApplicationDraft.builder()
                .user(user)
                .step(step)
                .type(type)
                .postingId(postingId)
                .middleCategoryId(middleCategoryId)
                .smallCategoryId(smallCategoryId)
                .selectedQuestionIds(normalizeQuestionIds(selectedQuestionIds))
                .build();
    }

    public void update(
            ApplicationDraftStep step,
            ApplyType type,
            Long postingId,
            Long middleCategoryId,
            Long smallCategoryId,
            List<Long> selectedQuestionIds
    ) {
        this.step = step;
        this.type = type;
        this.postingId = postingId;
        this.middleCategoryId = middleCategoryId;
        this.smallCategoryId = smallCategoryId;
        this.selectedQuestionIds.clear();
        this.selectedQuestionIds.addAll(normalizeQuestionIds(selectedQuestionIds));
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getSavedAt() {
        return updatedAt;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }

    private static List<Long> normalizeQuestionIds(List<Long> selectedQuestionIds) {
        if (selectedQuestionIds == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(selectedQuestionIds);
    }
}
