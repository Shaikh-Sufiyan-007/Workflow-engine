package com.workflow.engine.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWorkflowRequest {
    @NotBlank
    private String name;
    private String description;
    @NotBlank
    private String dagDefinition; // JSON string representing nodes & edges (React Flow compatible)
    private String cronExpression;
    private Boolean isActive;

    private List<CreateTaskDto> tasks;
    private List<CreateDependencyDto> dependencies;
}
