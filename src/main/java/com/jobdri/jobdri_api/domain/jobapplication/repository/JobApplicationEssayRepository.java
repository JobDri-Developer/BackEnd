package com.jobdri.jobdri_api.domain.jobapplication.repository;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationEssay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationEssaySourceResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationEssayPreviewResponse;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface JobApplicationEssayRepository extends JpaRepository<JobApplicationEssay, Long> {
    @Query(value = """
            select new com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationEssaySourceResponse(
                ja.id, ja.companyName, ja.postingName, ja.jobTitle, count(e))
            from JobApplicationEssay e join e.jobApplication ja
            where ja.user.id = :userId and ja.id <> :targetId
              and (:includeArchived = true or ja.archivedAt is null)
              and (lower(ja.companyName) like :pattern escape '!'
                or lower(ja.postingName) like :pattern escape '!'
                or lower(ja.jobTitle) like :pattern escape '!')
            group by ja.id, ja.companyName, ja.postingName, ja.jobTitle, ja.createdAt
            order by ja.createdAt desc, ja.id desc
            """, countQuery = """
            select count(distinct ja.id)
            from JobApplicationEssay e join e.jobApplication ja
            where ja.user.id = :userId and ja.id <> :targetId
              and (:includeArchived = true or ja.archivedAt is null)
              and (lower(ja.companyName) like :pattern escape '!'
                or lower(ja.postingName) like :pattern escape '!'
                or lower(ja.jobTitle) like :pattern escape '!')
            """)
    Page<JobApplicationEssaySourceResponse> findSources(
            @Param("userId") Long userId, @Param("targetId") Long targetId,
            @Param("pattern") String pattern, @Param("includeArchived") boolean includeArchived, Pageable pageable);

    @Query("""
            select new com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationEssayPreviewResponse(
                e.id, e.question, e.answer, e.displayOrder)
            from JobApplicationEssay e
            where e.jobApplication.id = :sourceId and e.jobApplication.user.id = :userId
            order by e.displayOrder, e.id
            """)
    List<JobApplicationEssayPreviewResponse> findPreviews(
            @Param("userId") Long userId, @Param("sourceId") Long sourceId);

    interface Count {
        Long getJobApplicationId();
        long getQuestionCount();
    }

    @Query("""
            select e.jobApplication.id as jobApplicationId, count(e) as questionCount
            from JobApplicationEssay e
            where e.jobApplication.user.id = :userId and e.jobApplication.archivedAt is null
            group by e.jobApplication.id
            """)
    List<Count> countActiveByUser(@Param("userId") Long userId);
}
