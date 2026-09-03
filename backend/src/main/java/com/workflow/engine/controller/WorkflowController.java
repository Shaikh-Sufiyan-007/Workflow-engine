package com.workflow.engine.controller;

import com.workflow.engine.dto.request.CreateWorkflowRequest;
import com.workflow.engine.dto.response.WorkflowResponse;
import com.workflow.engine.dto.response.WorkflowRunResponse;
import com.workflow.engine.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping
    public ResponseEntity<WorkflowResponse> createWorkflow(@Valid @RequestBody CreateWorkflowRequest request) {
        WorkflowResponse response = workflowService.createWorkflow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<WorkflowResponse>> getAllWorkflows() {
        return ResponseEntity.ok(workflowService.getAllWorkflows());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponse> getWorkflowById(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getWorkflowById(id));
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<WorkflowRunResponse> triggerWorkflow(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "MANUAL";
        WorkflowRunResponse response = workflowService.triggerWorkflow(id, "MANUAL:" + username);
        return ResponseEntity.ok(response);
    }
}
