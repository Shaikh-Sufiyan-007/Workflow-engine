package com.workflow.engine.dto.request;

import com.workflow.engine.enums.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskDto {
    private UUID id;
    @NotBlank
    private String name;
    @NotNull
    private TaskType taskType;
    private String config;
    private Integer maxRetries;
    private Integer retryBackoffSeconds;
}
