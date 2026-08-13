package com.workflow.engine.entity;

import com.workflow.engine.enums.TaskRunStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_run_id", nullable = false)
    private WorkflowRun workflowRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TaskRunStatus status;

    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private Integer attemptNumber = 1;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
