package com.workflow.engine.repository;

import com.workflow.engine.entity.WorkflowRun;
import com.workflow.engine.enums.WorkflowRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {
    List<WorkflowRun> findByWorkflowIdOrderByTriggeredAtDesc(UUID workflowId);
    List<WorkflowRun> findByStatus(WorkflowRunStatus status);
}
