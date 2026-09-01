package com.workflow.engine.websocket;

import com.workflow.engine.enums.TaskRunStatus;
import com.workflow.engine.enums.TaskType;
import com.workflow.engine.enums.WorkflowRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRunStatusMessage {
    private String eventType; // "TASK_UPDATE" or "WORKFLOW_UPDATE"
    private UUID workflowRunId;
    private UUID taskId;
    private String taskName;
    private TaskType taskType;
    private TaskRunStatus taskStatus;
    private WorkflowRunStatus workflowStatus;
    private Integer attemptNumber;
    private Instant startedAt;
    private Instant finishedAt;
    private String output;
    private String errorMessage;
}
