package com.workflow.engine.repository;

import com.workflow.engine.entity.TaskRun;
import com.workflow.engine.enums.TaskRunStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRunRepository extends JpaRepository<TaskRun, UUID> {

    List<TaskRun> findByWorkflowRunId(UUID workflowRunId);

    Optional<TaskRun> findByWorkflowRunIdAndTaskId(UUID workflowRunId, UUID taskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tr FROM TaskRun tr WHERE tr.id = :id")
    Optional<TaskRun> findByIdWithLock(@Param("id") UUID id);

    @Query(value = "SELECT * FROM task_runs WHERE workflow_run_id = :workflowRunId AND status = 'PENDING' FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<TaskRun> findPendingForUpdateSkipLocked(@Param("workflowRunId") UUID workflowRunId);
}
