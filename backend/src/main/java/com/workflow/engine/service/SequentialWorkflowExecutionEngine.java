package com.workflow.engine.service;

import com.workflow.engine.entity.*;
import com.workflow.engine.enums.TaskRunStatus;
import com.workflow.engine.enums.WorkflowRunStatus;
import com.workflow.engine.repository.*;
import com.workflow.engine.service.task.TaskExecutionResult;
import com.workflow.engine.service.task.TaskExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SequentialWorkflowExecutionEngine {

    private final WorkflowRunRepository workflowRunRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskRunRepository taskRunRepository;
    private final DagValidationService dagValidationService;
    private final TaskExecutorService taskExecutorService;

    @Transactional
    public WorkflowRun execute(UUID workflowRunId) {
        log.info("Starting sequential execution for WorkflowRun ID: {}", workflowRunId);
        WorkflowRun workflowRun = workflowRunRepository.findById(workflowRunId)
                .orElseThrow(() -> new IllegalArgumentException("WorkflowRun not found with ID: " + workflowRunId));

        workflowRun.setStatus(WorkflowRunStatus.RUNNING);
        workflowRunRepository.save(workflowRun);

        Workflow workflow = workflowRun.getWorkflow();
        List<Task> tasks = taskRepository.findByWorkflowId(workflow.getId());
        List<TaskDependency> dependencies = taskDependencyRepository.findByWorkflowId(workflow.getId());

        Map<UUID, Task> taskMap = tasks.stream().collect(Collectors.toMap(Task::getId, t -> t));
        Set<String> nodeIds = tasks.stream().map(t -> t.getId().toString()).collect(Collectors.toSet());

        List<DagValidationService.Edge> edges = dependencies.stream()
                .map(dep -> new DagValidationService.Edge(
                        dep.getDependsOnTask().getId().toString(),
                        dep.getTask().getId().toString()
                ))
                .collect(Collectors.toList());

        // Topological Sort
        List<String> sortedTaskIdsStr = dagValidationService.validateAndSort(nodeIds, edges);
        List<UUID> topologicalOrder = sortedTaskIdsStr.stream().map(UUID::fromString).collect(Collectors.toList());

        // Initialize TaskRun entities if not present
        Map<UUID, TaskRun> taskRunMap = new HashMap<>();
        for (Task task : tasks) {
            TaskRun taskRun = taskRunRepository.findByWorkflowRunIdAndTaskId(workflowRunId, task.getId())
                    .orElseGet(() -> TaskRun.builder()
                            .workflowRun(workflowRun)
                            .task(task)
                            .status(TaskRunStatus.PENDING)
                            .attemptNumber(1)
                            .build());
            taskRunMap.put(task.getId(), taskRunRepository.save(taskRun));
        }

        // Map to keep track of dependency task IDs (key: task_id, value: list of depends_on_task_ids)
        Map<UUID, List<UUID>> upstreamMap = new HashMap<>();
        for (Task task : tasks) {
            upstreamMap.put(task.getId(), new ArrayList<>());
        }
        for (TaskDependency dep : dependencies) {
            upstreamMap.get(dep.getTask().getId()).add(dep.getDependsOnTask().getId());
        }

        // Execute tasks in topological order
        for (UUID taskId : topologicalOrder) {
            Task task = taskMap.get(taskId);
            TaskRun taskRun = taskRunMap.get(taskId);

            List<UUID> upstreamIds = upstreamMap.get(taskId);
            boolean upstreamFailed = upstreamIds.stream().anyMatch(upId -> {
                TaskRun upRun = taskRunMap.get(upId);
                return upRun == null || upRun.getStatus() != TaskRunStatus.SUCCESS;
            });

            if (upstreamFailed) {
                log.warn("Skipping Task '{}' (ID: {}) due to unmet or failed upstream dependencies", task.getName(), taskId);
                taskRun.setStatus(TaskRunStatus.SKIPPED);
                taskRun.setFinishedAt(Instant.now());
                taskRun.setErrorMessage("Skipped due to upstream dependency failure");
                taskRunRepository.save(taskRun);
                continue;
            }

            // Execute Task
            log.info("Executing Task '{}' (ID: {})", task.getName(), taskId);
            taskRun.setStatus(TaskRunStatus.RUNNING);
            taskRun.setStartedAt(Instant.now());
            taskRunRepository.save(taskRun);

            TaskExecutionResult result = taskExecutorService.execute(task);

            taskRun.setFinishedAt(Instant.now());
            if (result.success()) {
                taskRun.setStatus(TaskRunStatus.SUCCESS);
                taskRun.setOutput(result.output());
                log.info("Task '{}' succeeded with output: {}", task.getName(), result.output());
            } else {
                taskRun.setStatus(TaskRunStatus.FAILED);
                taskRun.setErrorMessage(result.errorMessage());
                log.error("Task '{}' failed with error: {}", task.getName(), result.errorMessage());
            }
            taskRunRepository.save(taskRun);
        }

        // Determine final WorkflowRun status
        boolean allSuccess = taskRunMap.values().stream().allMatch(tr -> tr.getStatus() == TaskRunStatus.SUCCESS);
        workflowRun.setStatus(allSuccess ? WorkflowRunStatus.SUCCESS : WorkflowRunStatus.FAILED);
        workflowRun.setFinishedAt(Instant.now());
        WorkflowRun savedRun = workflowRunRepository.save(workflowRun);

        log.info("WorkflowRun ID: {} completed with final status: {}", workflowRunId, savedRun.getStatus());
        return savedRun;
    }
}
