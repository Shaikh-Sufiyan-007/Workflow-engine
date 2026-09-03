package com.workflow.engine.dto.response;

import com.workflow.engine.enums.WorkflowRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRunResponse {
    private UUID id;
    private UUID workflowId;
    private WorkflowRunStatus status;
    private Instant triggeredAt;
    private String triggeredBy;
    private Instant finishedAt;
    private List<TaskRunResponse> taskRuns;
}
