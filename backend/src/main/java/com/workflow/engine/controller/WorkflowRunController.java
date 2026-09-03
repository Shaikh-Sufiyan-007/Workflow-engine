package com.workflow.engine.controller;

import com.workflow.engine.dto.response.WorkflowRunResponse;
import com.workflow.engine.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class WorkflowRunController {

    private final WorkflowService workflowService;

    @GetMapping("/api/workflows/{workflowId}/runs")
    public ResponseEntity<List<WorkflowRunResponse>> getWorkflowRuns(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(workflowService.getWorkflowRuns(workflowId));
    }

    @GetMapping({"/api/workflows/{workflowId}/runs/{runId}", "/api/runs/{runId}"})
    public ResponseEntity<WorkflowRunResponse> getWorkflowRunById(
            @PathVariable(required = false) UUID workflowId,
            @PathVariable UUID runId
    ) {
        return ResponseEntity.ok(workflowService.getWorkflowRunById(runId));
    }
}

