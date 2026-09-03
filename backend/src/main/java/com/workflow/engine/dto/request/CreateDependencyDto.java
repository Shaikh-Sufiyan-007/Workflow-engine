package com.workflow.engine.dto.request;

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
public class CreateDependencyDto {
    @NotNull
    private UUID taskId;
    @NotNull
    private UUID dependsOnTaskId;
}
