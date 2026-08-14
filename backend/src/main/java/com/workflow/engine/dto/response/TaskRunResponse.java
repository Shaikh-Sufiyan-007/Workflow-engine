package com.workflow.engine.dto.response;

import com.workflow.engine.enums.TaskRunStatus;
import com.workflow.engine.enums.TaskType;
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
public class TaskRunResponse {
    private UUID id;
    private UUID taskId;
    private String taskName;
    private TaskType taskType;
    private TaskRunStatus status;
    private Integer attemptNumber;
    private Instant startedAt;
    private Instant finishedAt;
    private String output;
    private String errorMessage;
}
