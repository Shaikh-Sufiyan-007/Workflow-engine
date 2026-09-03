package com.workflow.engine.service;

import com.workflow.engine.entity.*;
import com.workflow.engine.enums.TaskRunStatus;
import com.workflow.engine.enums.TaskType;
import com.workflow.engine.enums.WorkflowRunStatus;
import com.workflow.engine.repository.*;
import com.workflow.engine.service.task.EmailTaskHandler;
import com.workflow.engine.service.task.ShellTaskHandler;
import com.workflow.engine.service.task.TaskExecutionResult;
import com.workflow.engine.service.task.TaskExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentWorkflowExecutionEngineTest {

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskDependencyRepository taskDependencyRepository;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private TaskRunRepository taskRunRepository;

    @Autowired
    private DagValidationService dagValidationService;

    @Autowired
    private ConcurrentWorkflowExecutionEngine concurrentEngine;

    private Workflow workflow;

    @BeforeEach
    void setUp() {
        taskDependencyRepository.deleteAll();
        taskRunRepository.deleteAll();
        workflowRunRepository.deleteAll();
        taskRepository.deleteAll();
        workflowRepository.deleteAll();

        workflow = Workflow.builder()
                .name("Concurrent Workflow Test")
                .description("Testing concurrency, retries, and locking")
                .dagDefinition("{}")
                .isActive(true)
                .build();
        workflow = workflowRepository.save(workflow);
    }

    @Test
    @DisplayName("Concurrent execution: Independent tasks (B & C) run concurrently in Diamond DAG")
    void testConcurrentDiamondExecution() {
        Task taskA = createTask("Task A", TaskType.SHELL_COMMAND, 3, 1);
        Task taskB = createTask("Task B", TaskType.SHELL_COMMAND, 3, 1);
        Task taskC = createTask("Task C", TaskType.DB_QUERY, 3, 1);
        Task taskD = createTask("Task D", TaskType.EMAIL, 3, 1);

        addDependency(taskB, taskA);
        addDependency(taskC, taskA);
        addDependency(taskD, taskB);
        addDependency(taskD, taskC);

        WorkflowRun run = createWorkflowRun();
        WorkflowRun result = concurrentEngine.execute(run.getId());

        assertEquals(WorkflowRunStatus.SUCCESS, result.getStatus());

        List<TaskRun> taskRuns = taskRunRepository.findByWorkflowRunId(run.getId());
        assertEquals(4, taskRuns.size());
        for (TaskRun tr : taskRuns) {
            assertEquals(TaskRunStatus.SUCCESS, tr.getStatus());
        }
    }

    @Test
    @DisplayName("Retry with exponential backoff: Task fails initial attempts and succeeds on retry")
    void testRetryBackoffSuccess() {
        Task taskA = createTask("Task A", TaskType.SHELL_COMMAND, 3, 1);

        AtomicInteger attemptCounter = new AtomicInteger(0);
        TaskExecutorService retryingExecutor = new TaskExecutorService(List.of(
                new ShellTaskHandler() {
                    @Override
                    public TaskExecutionResult execute(Task task) {
                        int current = attemptCounter.incrementAndGet();
                        if (current < 3) {
                            return TaskExecutionResult.failure("Transient error attempt " + current);
                        }
                        return TaskExecutionResult.success("Success on attempt " + current);
                    }
                }
        ));

        ConcurrentWorkflowExecutionEngine engineWithRetry = new ConcurrentWorkflowExecutionEngine(
                workflowRunRepository, taskRepository, taskDependencyRepository, taskRunRepository,
                dagValidationService, retryingExecutor, new WebSocketBroadcaster(null)
        );

        WorkflowRun run = createWorkflowRun();
        WorkflowRun result = engineWithRetry.execute(run.getId());

        assertEquals(WorkflowRunStatus.SUCCESS, result.getStatus());
        assertEquals(3, attemptCounter.get());

        TaskRun tr = taskRunRepository.findByWorkflowRunIdAndTaskId(run.getId(), taskA.getId()).orElseThrow();
        assertEquals(TaskRunStatus.SUCCESS, tr.getStatus());
        assertEquals(3, tr.getAttemptNumber());
    }

    @Test
    @DisplayName("Locking & idempotency: Two concurrent workers racing for the same task run - only one claims and executes it")
    void testWorkerRacingLocking() throws InterruptedException {
        Task taskA = createTask("Task A", TaskType.SHELL_COMMAND, 1, 1);
        WorkflowRun run = createWorkflowRun();

        TaskRun taskRun = TaskRun.builder()
                .workflowRun(run)
                .task(taskA)
                .status(TaskRunStatus.PENDING)
                .attemptNumber(1)
                .build();
        taskRun = taskRunRepository.save(taskRun);

        int numberOfWorkers = 5;
        ExecutorService workerPool = Executors.newFixedThreadPool(numberOfWorkers);
        CountDownLatch readyLatch = new CountDownLatch(numberOfWorkers);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger claimedCount = new AtomicInteger(0);

        final TaskRun targetRun = taskRun;
        for (int i = 0; i < numberOfWorkers; i++) {
            workerPool.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    boolean claimed = concurrentEngine.claimTaskForExecution(targetRun.getId());
                    if (claimed) {
                        claimedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        workerPool.shutdown();

        assertEquals(1, claimedCount.get(), "Exactly ONE worker thread should claim the pending task run");
    }

    private Task createTask(String name, TaskType type, int maxRetries, int backoffSec) {
        Task task = Task.builder()
                .workflow(workflow)
                .name(name)
                .taskType(type)
                .config("{\"cmd\":\"echo " + name + "\"}")
                .maxRetries(maxRetries)
                .retryBackoffSeconds(backoffSec)
                .build();
        return taskRepository.save(task);
    }

    private void addDependency(Task task, Task dependsOn) {
        TaskDependency dep = TaskDependency.builder()
                .id(new TaskDependencyId(task.getId(), dependsOn.getId()))
                .task(task)
                .dependsOnTask(dependsOn)
                .build();
        taskDependencyRepository.save(dep);
    }

    private WorkflowRun createWorkflowRun() {
        WorkflowRun run = WorkflowRun.builder()
                .workflow(workflow)
                .status(WorkflowRunStatus.PENDING)
                .triggeredBy("MANUAL:test")
                .build();
        return workflowRunRepository.save(run);
    }
}
