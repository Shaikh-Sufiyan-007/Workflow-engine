package com.workflow.engine.repository;

import com.workflow.engine.entity.TaskDependency;
import com.workflow.engine.entity.TaskDependencyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskDependencyRepository extends JpaRepository<TaskDependency, TaskDependencyId> {

    @Query("SELECT td FROM TaskDependency td WHERE td.task.workflow.id = :workflowId")
    List<TaskDependency> findByWorkflowId(@Param("workflowId") UUID workflowId);

    @Query("SELECT td.dependsOnTask.id FROM TaskDependency td WHERE td.task.id = :taskId")
    List<UUID> findUpstreamTaskIds(@Param("taskId") UUID taskId);

    @Query("SELECT td.task.id FROM TaskDependency td WHERE td.dependsOnTask.id = :taskId")
    List<UUID> findDownstreamTaskIds(@Param("taskId") UUID taskId);
}
