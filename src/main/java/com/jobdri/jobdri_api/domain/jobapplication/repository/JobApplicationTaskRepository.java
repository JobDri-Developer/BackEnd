package com.jobdri.jobdri_api.domain.jobapplication.repository;

import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationIncompleteTaskResponse;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationTask;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobApplicationTaskRepository extends JpaRepository<JobApplicationTask, Long> {
    /** Reads summaries without loading checklist text and includes tasks with empty checklists. */
    @Query("""
            select new com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationIncompleteTaskResponse(
                task.id, application.id, application.postingName, application.companyName, task.title, task.dueDate,
                sum(case when item.completed = true then 1 else 0 end), count(item.content)
            )
            from JobApplicationTask task
            join task.jobApplication application
            left join task.checklistItems item
            where application.user.id = :userId and task.completed = false
            group by task.id, application.id, application.postingName, application.companyName, task.title, task.dueDate
            """)
    List<JobApplicationIncompleteTaskResponse> findIncompleteByUserId(@Param("userId") Long userId);

    /** Fetches an application's tasks and their checklists together in registration order. */
    @EntityGraph(attributePaths = "checklistItems")
    List<JobApplicationTask> findAllByJobApplicationIdOrderByIdAsc(Long jobApplicationId);

    /** Looks up a task only within the given application. */
    Optional<JobApplicationTask> findByIdAndJobApplicationId(Long id, Long jobApplicationId);
}
