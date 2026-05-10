package com.jobdri.jobdri_api.domain.company.entity;

import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompanySize size;

    @Builder.Default
    @OneToMany(mappedBy = "company")
    private List<JobPosting> jobPostings = new ArrayList<>();

    public static Company create(String name, CompanySize size) {
        return Company.builder()
                .name(name)
                .size(size)
                .build();
    }
}
