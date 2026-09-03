package com.workflow.engine.service;

import com.workflow.engine.entity.*;
import com.workflow.engine.enums.TaskRunStatus;
import com.workflow.engine.enums.TaskType;
import com.workflow.engine.enums.WorkflowRunStatus;
import com.workflow.engine.repository.*;
import com.workflow.engine.service.task.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SequentialWorkflowExecutionEngineTest {

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
    private SequentialWorkflowExecutionEngine executionEngine;

    private Workflow workflow;

    @BeforeEach
    void setUp() {
        workflow = Workflow.builder()
                .name("Test Workflow")
                .description("Sequential Engine Test Workflow")
                .dagDefinition("{}")
                .isActive(true)
                .build();
        workflow = workflowRepository.save(workflow);
    }

    @Test
    @DisplayName("Linear DAG (A -> B -> C) executes sequentially and succeeds")
    void testLinearDagExecution() {
        Task taskA = createTask("Task A", TaskType.SHELL_COMMAND);
        Task taskB = createTask("Task B", TaskType.HTTP_CALL);
        Task taskC = createTask("Task C", TaskType.EMAIL);

        addDependency(taskB, taskA); // B depends on A
        addDependency(taskC, taskB); // C depends on B

        WorkflowRun run = createWorkflowRun();

        WorkflowRun result = executionEngine.execute(run.getId());

        assertEquals(WorkflowRunStatus.SUCCESS, result.getStatus());

        List<TaskRun> taskRuns = taskRunRepository.findByWorkflowRunId(run.getId());
        assertEquals(3, taskRuns.size());

        Map<String, TaskRunStatus> statusMap = taskRuns.stream()
                .collect(Collectors.toMap(tr -> tr.getTask().getName(), TaskRun::getStatus));

        assertEquals(TaskRunStatus.SUCCESS, statusMap.get("Task A"));
        assertEquals(TaskRunStatus.SUCCESS, statusMap.get("Task B"));
        assertEquals(TaskRunStatus.SUCCESS, statusMap.get("Task C"));
    }

    @Test
    @DisplayName("Diamond DAG (A -> B, A -> C, B -> D, C -> D) executes in topological order")
    void testDiamondDagExecution() {
        Task taskA = createTask("Task A", TaskType.SHELL_COMMAND);
        Task taskB = createTask("Task B", TaskType.HTTP_CALL);
        Task taskC = createTask("Task C", TaskType.DB_QUERY);
        Task taskD = createTask("Task D", TaskType.EMAIL);

        addDependency(taskB, taskA); // B depends on A
        addDependency(taskC, taskA); // C depends on A
        addDependency(taskD, taskB); // D depends on B
        addDependency(taskD, taskC); // D depends on C

        WorkflowRun run = createWorkflowRun();

        WorkflowRun result = executionEngine.execute(run.getId());

        assertEquals(WorkflowRunStatus.SUCCESS, result.getStatus());

        List<TaskRun> taskRuns = taskRunRepository.findByWorkflowRunId(run.getId());
        assertEquals(4, taskRuns.size());

        for (TaskRun tr : taskRuns) {
            assertEquals(TaskRunStatus.SUCCESS, tr.getStatus(), "All tasks in diamond DAG should succeed");
            assertNotNull(tr.getStartedAt());
            assertNotNull(tr.getFinishedAt());
        }
    }

    @Test
    @DisplayName("Task failure should skip downstream dependent tasks")
    void testFailurePropagationAndSkippedTasks() {
        Task taskA = createTask("Task A", TaskType.SHELL_COMMAND);
        // Task B will fail because we mock or supply unknown/failing config
        Task taskB = createTask("Task B", TaskType.SHELL_COMMAND);
        Task taskC = createTask("Task C", TaskType.EMAIL);

        addDependency(taskB, taskA);
        addDependency(taskC, taskB); // C depends on B

        // Create a custom engine runner or override executor behavior for failure test
        // For testing, let's create a custom engine instance with a mock handler that fails on Task B
        TaskExecutorService failingExecutorService = new TaskExecutorService(List.of(
                new ShellTaskHandler() {
                    @Override
                    public TaskExecutionResult execute(Task task) {
                        if ("Task B".equals(task.getName())) {
                            return TaskExecutionResult.failure("Simulated failure in Task B");
                        }
                        return super.execute(task);
                    }
                },
                new EmailTaskHandler()
        ));

        SequentialWorkflowExecutionEngine customEngine = new SequentialWorkflowExecutionEngine(
                workflowRunRepository, taskRepository, taskDependencyRepository, taskRunRepository,
                new DagValidationService(), failingExecutorService
        );

        WorkflowRun run = createWorkflowRun();
        WorkflowRun result = customEngine.execute(run.getId());

        assertEquals(WorkflowRunStatus.FAILED, result.getStatus());

        List<TaskRun> taskRuns = taskRunRepository.findByWorkflowRunId(run.getId());
        Map<String, TaskRunStatus> statusMap = taskRuns.stream()
                .collect(Collectors.toMap(tr -> tr.getTask().getName(), TaskRun::getStatus));

        assertEquals(TaskRunStatus.SUCCESS, statusMap.get("Task A"));
        assertEquals(TaskRunStatus.FAILED, statusMap.get("Task B"));
        assertEquals(TaskRunStatus.SKIPPED, statusMap.get("Task C"), "Task C should be SKIPPED due to Task B failure");
    }

    private Task createTask(String name, TaskType type) {
        Task task = Task.builder()
                .workflow(workflow)
                .name(name)
                .taskType(type)
                .config("{\"cmd\":\"echo " + name + "\"}")
                .maxRetries(3)
                .retryBackoffSeconds(1)
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
