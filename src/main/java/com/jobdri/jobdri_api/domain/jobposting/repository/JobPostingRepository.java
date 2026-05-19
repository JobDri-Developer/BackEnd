package com.jobdri.jobdri_api.domain.jobposting.repository;

import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    List<JobPosting> findAllByCompanyId(Long companyId);
    List<JobPosting> findTop5ByDetailClassificationIdOrderByIdDesc(Long detailClassificationId);
    List<JobPosting> findTop5ByCompanyIdOrderByIdDesc(Long companyId);

    @Query(value = """
            SELECT jp.*
            FROM job_postings jp
            WHERE jp.detail_classification_id = :detailClassificationId
               OR (:companyId IS NOT NULL AND jp.company_id = :companyId)
            ORDER BY
                CASE
                    WHEN :companyId IS NOT NULL
                     AND jp.company_id = :companyId
                     AND jp.detail_classification_id = :detailClassificationId THEN 3
                    WHEN jp.detail_classification_id = :detailClassificationId THEN 2
                    WHEN :companyId IS NOT NULL
                     AND jp.company_id = :companyId THEN 1
                    ELSE 0
                END DESC,
                jp.id DESC
            LIMIT 5
            """, nativeQuery = true)
    List<JobPosting> findTop5ReferencePostings(
            @Param("companyId") Long companyId,
            @Param("detailClassificationId") Long detailClassificationId
    );
}
