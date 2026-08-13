package com.workflow.engine.entity;

import com.workflow.engine.enums.WorkflowRunStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private WorkflowRunStatus status;

    @Column(name = "triggered_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant triggeredAt = Instant.now();

    @Column(name = "triggered_by", nullable = false, length = 100)
    private String triggeredBy;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
