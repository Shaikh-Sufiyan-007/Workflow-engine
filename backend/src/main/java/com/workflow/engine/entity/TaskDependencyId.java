package com.workflow.engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TaskDependencyId implements Serializable {

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "depends_on_task_id")
    private UUID dependsOnTaskId;
}
