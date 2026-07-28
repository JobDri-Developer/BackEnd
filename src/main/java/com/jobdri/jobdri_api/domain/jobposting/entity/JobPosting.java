package com.jobdri.jobdri_api.domain.jobposting.entity;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
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
@Table(name = "job_postings")
public class JobPosting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detail_classification_id", nullable = false)
    private DetailClassification detailClassification;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobPostingProfileColor profileColor = JobPostingProfileColor.DEFAULT;

    @Column(nullable = false, length = 255)
    private String postingName;

    @Column(nullable = false, length = 255)
    private String jobTitle;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String task;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String requirement;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String preferred;

    @Builder.Default
    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MockApply> mockApplies = new ArrayList<>();

    public static JobPosting create(
            User user,
            Company company,
            DetailClassification detailClassification,
            JobPostingProfileColor profileColor,
            String postingName,
            String jobTitle,
            String task,
            String requirement,
            String preferred
    ) {
        return JobPosting.builder()
                .user(user)
                .company(company)
                .detailClassification(detailClassification)
                .profileColor(profileColor)
                .postingName(postingName)
                .jobTitle(jobTitle)
                .task(task)
                .requirement(requirement)
                .preferred(preferred)
                .build();
    }

    public static JobPosting create(
            User user,
            Company company,
            DetailClassification detailClassification,
            String task,
            String requirement,
            String preferred
    ) {
        String defaultJobTitle = detailClassification.getDetailName();
        return create(
                user,
                company,
                detailClassification,
                JobPostingProfileColor.DEFAULT,
                defaultJobTitle,
                defaultJobTitle,
                task,
                requirement,
                preferred
        );
    }

    public void update(
            User user,
            Company company,
            DetailClassification detailClassification,
            JobPostingProfileColor profileColor,
            String postingName,
            String jobTitle,
            String task,
            String requirement,
            String preferred
    ) {
        this.user = user;
        this.company = company;
        this.detailClassification = detailClassification;
        this.profileColor = profileColor;
        this.postingName = postingName;
        this.jobTitle = jobTitle;
        this.task = task;
        this.requirement = requirement;
        this.preferred = preferred;
    }
}
