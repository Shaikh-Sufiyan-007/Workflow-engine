package com.workflow.engine.dto.response;

import com.workflow.engine.dto.request.CreateDependencyDto;
import com.workflow.engine.dto.request.CreateTaskDto;
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
public class WorkflowResponse {
    private UUID id;
    private String name;
    private String description;
    private String dagDefinition;
    private String cronExpression;
    private Boolean isActive;
    private Instant createdAt;
    private List<CreateTaskDto> tasks;
    private List<CreateDependencyDto> dependencies;
}
