package com.workflow.engine.service;

import com.workflow.engine.entity.TaskRun;
import com.workflow.engine.entity.WorkflowRun;
import com.workflow.engine.websocket.TaskRunStatusMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastTaskRunUpdate(TaskRun taskRun) {
        if (messagingTemplate == null) return;
        try {
            String destination = "/topic/workflow-runs/" + taskRun.getWorkflowRun().getId();
            TaskRunStatusMessage message = TaskRunStatusMessage.builder()
                    .eventType("TASK_UPDATE")
                    .workflowRunId(taskRun.getWorkflowRun().getId())
                    .taskId(taskRun.getTask().getId())
                    .taskName(taskRun.getTask().getName())
                    .taskType(taskRun.getTask().getTaskType())
                    .taskStatus(taskRun.getStatus())
                    .attemptNumber(taskRun.getAttemptNumber())
                    .startedAt(taskRun.getStartedAt())
                    .finishedAt(taskRun.getFinishedAt())
                    .output(taskRun.getOutput())
                    .errorMessage(taskRun.getErrorMessage())
                    .build();

            log.info("Broadcasting STOMP task update to {}: Task='{}', Status={}", destination, taskRun.getTask().getName(), taskRun.getStatus());
            messagingTemplate.convertAndSend(destination, message);
        } catch (Exception e) {
            log.warn("WebSocket broadcasting failed: {}", e.getMessage());
        }
    }

    public void broadcastWorkflowRunUpdate(WorkflowRun run) {
        if (messagingTemplate == null) return;
        try {
            String destination = "/topic/workflow-runs/" + run.getId();
            TaskRunStatusMessage message = TaskRunStatusMessage.builder()
                    .eventType("WORKFLOW_UPDATE")
                    .workflowRunId(run.getId())
                    .workflowStatus(run.getStatus())
                    .finishedAt(run.getFinishedAt())
                    .build();

            log.info("Broadcasting STOMP workflow update to {}: Status={}", destination, run.getStatus());
            messagingTemplate.convertAndSend(destination, message);
        } catch (Exception e) {
            log.warn("WebSocket broadcasting failed: {}", e.getMessage());
        }
    }
}
