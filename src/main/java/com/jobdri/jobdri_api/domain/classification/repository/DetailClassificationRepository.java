package com.jobdri.jobdri_api.domain.classification.repository;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingClassificationCandidateProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DetailClassificationRepository extends JpaRepository<DetailClassification, Long> {
    List<DetailClassification> findAllByMiddleClassificationId(Long middleClassificationId);
    Optional<DetailClassification> findByDetailName(String detailName);
    long countByDetailName(String detailName);

    @Query("""
            SELECT dc
            FROM DetailClassification dc
            JOIN dc.middleClassification mc
            JOIN mc.classification c
            WHERE lower(c.bigName) = lower(:bigName)
              AND lower(mc.middleName) = lower(:middleName)
              AND lower(dc.detailName) = lower(:detailName)
            """)
    Optional<DetailClassification> findByHierarchyNames(
            @Param("bigName") String bigName,
            @Param("middleName") String middleName,
            @Param("detailName") String detailName
    );

    @Query(value = """
            SELECT
                dc.id AS detailClassificationId,
                dc.detail_name AS detailClassificationName,
                mc.middle_name AS middleClassificationName,
                c.big_name AS bigClassificationName,
                GREATEST(
                    word_similarity(lower(dc.detail_name), lower(:query)),
                    word_similarity(lower(mc.middle_name), lower(:query)),
                    word_similarity(lower(concat(c.big_name, ' ', mc.middle_name, ' ', dc.detail_name)), lower(:query))
                ) AS score
            FROM detail_classifications dc
            JOIN middle_classifications mc ON dc.middle_classification_id = mc.id
            JOIN classifications c ON mc.classification_id = c.id
            ORDER BY score DESC, dc.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<JobPostingClassificationCandidateProjection> findTopCandidatesByTrigram(
            @Param("query") String query,
            @Param("limit") int limit
    );
}
