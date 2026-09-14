package com.jobdri.jobdri_api.domain.jobapplication.repository;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationEssay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface JobApplicationEssayRepository extends JpaRepository<JobApplicationEssay, Long> {
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
