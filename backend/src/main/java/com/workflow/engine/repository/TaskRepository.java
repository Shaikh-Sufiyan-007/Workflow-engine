package com.workflow.engine.repository;

import com.workflow.engine.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByWorkflowId(UUID workflowId);
    void deleteByWorkflowId(UUID workflowId);
}
