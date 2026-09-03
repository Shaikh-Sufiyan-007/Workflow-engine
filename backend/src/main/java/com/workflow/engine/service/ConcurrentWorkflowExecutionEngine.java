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
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcurrentWorkflowExecutionEngine {

    private final WorkflowRunRepository workflowRunRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskRunRepository taskRunRepository;
    private final DagValidationService dagValidationService;
    private final TaskExecutorService taskExecutorService;
    private final WebSocketBroadcaster webSocketBroadcaster;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * Atomically claims a TaskRun for execution using state lock to prevent duplicate worker pickup.
     * Returns true if successfully claimed by caller, false if already claimed by another worker.
     */
    @Transactional
    public boolean claimTaskForExecution(UUID taskRunId) {
        Optional<TaskRun> optionalTaskRun = taskRunRepository.findByIdWithLock(taskRunId);
        if (optionalTaskRun.isEmpty()) {
            return false;
        }

        TaskRun taskRun = optionalTaskRun.get();
        if (taskRun.getStatus() == TaskRunStatus.PENDING || taskRun.getStatus() == TaskRunStatus.RETRYING) {
            taskRun.setStatus(TaskRunStatus.RUNNING);
            taskRun.setStartedAt(Instant.now());
            TaskRun saved = taskRunRepository.save(taskRun);
            if (webSocketBroadcaster != null) {
                webSocketBroadcaster.broadcastTaskRunUpdate(saved);
            }
            log.info("Worker thread successfully claimed TaskRun ID: {}", taskRunId);
            return true;
        }

        log.info("TaskRun ID: {} already claimed by another worker (Status: {})", taskRunId, taskRun.getStatus());
        return false;
    }

    public WorkflowRun execute(UUID workflowRunId) {
        log.info("Starting concurrent execution engine for WorkflowRun ID: {}", workflowRunId);

        WorkflowRun workflowRun = workflowRunRepository.findById(workflowRunId)
                .orElseThrow(() -> new IllegalArgumentException("WorkflowRun not found with ID: " + workflowRunId));

        workflowRun.setStatus(WorkflowRunStatus.RUNNING);
        workflowRunRepository.save(workflowRun);

        Workflow workflow = workflowRun.getWorkflow();
        List<Task> tasks = taskRepository.findByWorkflowId(workflow.getId());
        List<TaskDependency> dependencies = taskDependencyRepository.findByWorkflowId(workflow.getId());

        Set<String> nodeIds = tasks.stream().map(t -> t.getId().toString()).collect(Collectors.toSet());
        List<DagValidationService.Edge> edges = dependencies.stream()
                .map(dep -> new DagValidationService.Edge(
                        dep.getDependsOnTask().getId().toString(),
                        dep.getTask().getId().toString()
                ))
                .collect(Collectors.toList());

        // Validate DAG graph structure
        dagValidationService.validateAndSort(nodeIds, edges);

        Map<UUID, Task> taskMap = tasks.stream().collect(Collectors.toMap(Task::getId, t -> t));
        Map<UUID, List<UUID>> upstreamMap = new HashMap<>();
        for (Task task : tasks) {
            upstreamMap.put(task.getId(), new ArrayList<>());
        }
        for (TaskDependency dep : dependencies) {
            upstreamMap.get(dep.getTask().getId()).add(dep.getDependsOnTask().getId());
        }

        // Initialize TaskRun entries
        Map<UUID, TaskRun> taskRunMap = new ConcurrentHashMap<>();
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

        Set<UUID> completedTasks = ConcurrentHashMap.newKeySet();
        Set<UUID> runningTasks = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(tasks.size());

        // Reactive scheduling loop until all tasks are accounted for
        while (completedTasks.size() < tasks.size()) {
            boolean progressMade = false;

            for (Task task : tasks) {
                UUID taskId = task.getId();
                if (completedTasks.contains(taskId) || runningTasks.contains(taskId)) {
                    continue;
                }

                TaskRun currentRun = taskRunMap.get(taskId);
                if (currentRun.getStatus() == TaskRunStatus.SKIPPED || currentRun.getStatus() == TaskRunStatus.FAILED) {
                    completedTasks.add(taskId);
                    latch.countDown();
                    progressMade = true;
                    continue;
                }

                List<UUID> upstreamIds = upstreamMap.get(taskId);
                boolean allUpstreamSucceeded = upstreamIds.stream()
                        .allMatch(upId -> {
                            TaskRun upRun = taskRunMap.get(upId);
                            return upRun != null && upRun.getStatus() == TaskRunStatus.SUCCESS;
                        });

                boolean anyUpstreamFailedOrSkipped = upstreamIds.stream()
                        .anyMatch(upId -> {
                            TaskRun upRun = taskRunMap.get(upId);
                            return upRun != null && (upRun.getStatus() == TaskRunStatus.FAILED || upRun.getStatus() == TaskRunStatus.SKIPPED);
                        });

                if (anyUpstreamFailedOrSkipped) {
                    currentRun.setStatus(TaskRunStatus.SKIPPED);
                    currentRun.setFinishedAt(Instant.now());
                    currentRun.setErrorMessage("Skipped due to upstream failure");
                    taskRunRepository.save(currentRun);
                    completedTasks.add(taskId);
                    latch.countDown();
                    progressMade = true;
                    continue;
                }

                if (allUpstreamSucceeded) {
                    runningTasks.add(taskId);
                    progressMade = true;

                    executorService.submit(() -> {
                        try {
                            executeTaskWithRetry(currentRun, task, taskRunMap);
                        } finally {
                            runningTasks.remove(taskId);
                            completedTasks.add(taskId);
                            latch.countDown();
                        }
                    });
                }
            }

            if (!progressMade && completedTasks.size() < tasks.size()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean allSuccess = taskRunMap.values().stream().allMatch(tr -> tr.getStatus() == TaskRunStatus.SUCCESS);
        workflowRun.setStatus(allSuccess ? WorkflowRunStatus.SUCCESS : WorkflowRunStatus.FAILED);
        workflowRun.setFinishedAt(Instant.now());
        WorkflowRun savedRun = workflowRunRepository.save(workflowRun);
        if (webSocketBroadcaster != null) {
            webSocketBroadcaster.broadcastWorkflowRunUpdate(savedRun);
        }
        return savedRun;
    }

    private void executeTaskWithRetry(TaskRun taskRun, Task task, Map<UUID, TaskRun> taskRunMap) {
        int maxRetries = task.getMaxRetries() != null ? task.getMaxRetries() : 3;
        int backoffSeconds = task.getRetryBackoffSeconds() != null ? task.getRetryBackoffSeconds() : 2;

        while (taskRun.getAttemptNumber() <= maxRetries) {
            boolean claimed = claimTaskForExecution(taskRun.getId());
            if (!claimed) {
                log.info("TaskRun ID: {} could not be claimed.", taskRun.getId());
                return;
            }

            TaskExecutionResult result = taskExecutorService.execute(task);
            if (result.success()) {
                taskRun.setStatus(TaskRunStatus.SUCCESS);
                taskRun.setOutput(result.output());
                taskRun.setFinishedAt(Instant.now());
                TaskRun saved = taskRunRepository.save(taskRun);
                taskRunMap.put(task.getId(), saved);
                if (webSocketBroadcaster != null) {
                    webSocketBroadcaster.broadcastTaskRunUpdate(saved);
                }
                log.info("Concurrent Execution: Task '{}' succeeded", task.getName());
                return;
            }

            log.warn("Concurrent Execution: Task '{}' attempt {} failed: {}", task.getName(), taskRun.getAttemptNumber(), result.errorMessage());
            if (taskRun.getAttemptNumber() < maxRetries) {
                taskRun.setAttemptNumber(taskRun.getAttemptNumber() + 1);
                taskRun.setStatus(TaskRunStatus.RETRYING);
                TaskRun saved = taskRunRepository.save(taskRun);
                if (webSocketBroadcaster != null) {
                    webSocketBroadcaster.broadcastTaskRunUpdate(saved);
                }

                long waitTimeMs = (long) (backoffSeconds * Math.pow(2, taskRun.getAttemptNumber() - 2)) * 1000L;
                try {
                    Thread.sleep(Math.min(waitTimeMs, 2000L)); // Cap sleep for test speed
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                taskRun.setStatus(TaskRunStatus.FAILED);
                taskRun.setErrorMessage(result.errorMessage());
                taskRun.setFinishedAt(Instant.now());
                TaskRun saved = taskRunRepository.save(taskRun);
                taskRunMap.put(task.getId(), saved);
                if (webSocketBroadcaster != null) {
                    webSocketBroadcaster.broadcastTaskRunUpdate(saved);
                }
                return;
            }
        }
    }
}
