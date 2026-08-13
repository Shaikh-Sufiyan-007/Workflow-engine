package com.workflow.engine.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "task_dependencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskDependency {

    @EmbeddedId
    private TaskDependencyId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("taskId")
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("dependsOnTaskId")
    @JoinColumn(name = "depends_on_task_id", nullable = false)
    private Task dependsOnTask;
}
