package com.workflow.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.dto.request.CreateDependencyDto;
import com.workflow.engine.dto.request.CreateTaskDto;
import com.workflow.engine.dto.request.CreateWorkflowRequest;
import com.workflow.engine.dto.response.TaskRunResponse;
import com.workflow.engine.dto.response.WorkflowResponse;
import com.workflow.engine.dto.response.WorkflowRunResponse;
import com.workflow.engine.entity.*;
import com.workflow.engine.enums.TaskType;
import com.workflow.engine.enums.WorkflowRunStatus;
import com.workflow.engine.exception.ResourceNotFoundException;
import com.workflow.engine.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final TaskRunRepository taskRunRepository;
    private final DagValidationService dagValidationService;
    private final ConcurrentWorkflowExecutionEngine concurrentEngine;
    private final SchedulerService schedulerService;
    private final ObjectMapper objectMapper;

    @Transactional
    public WorkflowResponse createWorkflow(CreateWorkflowRequest request) {
        log.info("Creating new workflow: {}", request.getName());

        // Parse DAG definition or tasks/dependencies
        List<CreateTaskDto> taskDtos = request.getTasks();
        List<CreateDependencyDto> depDtos = request.getDependencies();

        if ((taskDtos == null || taskDtos.isEmpty()) && request.getDagDefinition() != null) {
            ParsedDag parsed = parseDagDefinition(request.getDagDefinition());
            taskDtos = parsed.tasks();
            depDtos = parsed.dependencies();
        }

        if (taskDtos == null) {
            taskDtos = List.of();
        }
        if (depDtos == null) {
            depDtos = List.of();
        }

        // Validate DAG for cycles
        Set<String> nodeIds = taskDtos.stream().map(t -> t.getId().toString()).collect(Collectors.toSet());
        List<DagValidationService.Edge> edges = depDtos.stream()
                .map(d -> new DagValidationService.Edge(d.getDependsOnTaskId().toString(), d.getTaskId().toString()))
                .collect(Collectors.toList());

        dagValidationService.validateAndSort(nodeIds, edges);

        // Persist Workflow
        Workflow workflow = Workflow.builder()
                .name(request.getName())
                .description(request.getDescription())
                .dagDefinition(request.getDagDefinition() != null ? request.getDagDefinition() : "{}")
                .cronExpression(request.getCronExpression())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
        workflow = workflowRepository.save(workflow);

        // Persist Tasks
        Map<UUID, Task> taskMap = new HashMap<>();
        for (CreateTaskDto dto : taskDtos) {
            Task task = Task.builder()
                    .id(dto.getId() != null ? dto.getId() : UUID.randomUUID())
                    .workflow(workflow)
                    .name(dto.getName())
                    .taskType(dto.getTaskType() != null ? dto.getTaskType() : TaskType.SHELL_COMMAND)
                    .config(dto.getConfig() != null ? dto.getConfig() : "{}")
                    .maxRetries(dto.getMaxRetries() != null ? dto.getMaxRetries() : 3)
                    .retryBackoffSeconds(dto.getRetryBackoffSeconds() != null ? dto.getRetryBackoffSeconds() : 5)
                    .build();
            taskMap.put(task.getId(), taskRepository.save(task));
        }

        // Persist Dependencies
        for (CreateDependencyDto depDto : depDtos) {
            Task task = taskMap.get(depDto.getTaskId());
            Task dependsOn = taskMap.get(depDto.getDependsOnTaskId());
            if (task != null && dependsOn != null) {
                TaskDependency dep = TaskDependency.builder()
                        .id(new TaskDependencyId(task.getId(), dependsOn.getId()))
                        .task(task)
                        .dependsOnTask(dependsOn)
                        .build();
                taskDependencyRepository.save(dep);
            }
        }

        // Schedule Quartz Cron if present
        if (Boolean.TRUE.equals(workflow.getIsActive()) && workflow.getCronExpression() != null) {
            schedulerService.scheduleWorkflowCron(workflow);
        }

        return mapToWorkflowResponse(workflow);
    }

    public List<WorkflowResponse> getAllWorkflows() {
        return workflowRepository.findAll().stream()
                .map(this::mapToWorkflowResponse)
                .collect(Collectors.toList());
    }

    public WorkflowResponse getWorkflowById(UUID id) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found with ID: " + id));
        return mapToWorkflowResponse(workflow);
    }

    @Transactional
    public WorkflowRunResponse triggerWorkflow(UUID workflowId, String triggeredBy) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found with ID: " + workflowId));

        WorkflowRun run = WorkflowRun.builder()
                .workflow(workflow)
                .status(WorkflowRunStatus.PENDING)
                .triggeredAt(Instant.now())
                .triggeredBy(triggeredBy != null ? triggeredBy : "MANUAL")
                .build();
        run = workflowRunRepository.save(run);

        UUID runId = run.getId();
        CompletableFuture.runAsync(() -> concurrentEngine.execute(runId));

        return mapToWorkflowRunResponse(run);
    }

    public List<WorkflowRunResponse> getWorkflowRuns(UUID workflowId) {
        return workflowRunRepository.findByWorkflowIdOrderByTriggeredAtDesc(workflowId).stream()
                .map(this::mapToWorkflowRunResponse)
                .collect(Collectors.toList());
    }

    public WorkflowRunResponse getWorkflowRunById(UUID runId) {
        WorkflowRun run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowRun not found with ID: " + runId));
        return mapToWorkflowRunResponse(run);
    }

    private WorkflowResponse mapToWorkflowResponse(Workflow workflow) {
        List<Task> tasks = taskRepository.findByWorkflowId(workflow.getId());
        List<TaskDependency> deps = taskDependencyRepository.findByWorkflowId(workflow.getId());

        List<CreateTaskDto> taskDtos = tasks.stream()
                .map(t -> CreateTaskDto.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .taskType(t.getTaskType())
                        .config(t.getConfig())
                        .maxRetries(t.getMaxRetries())
                        .retryBackoffSeconds(t.getRetryBackoffSeconds())
                        .build())
                .collect(Collectors.toList());

        List<CreateDependencyDto> depDtos = deps.stream()
                .map(d -> CreateDependencyDto.builder()
                        .taskId(d.getTask().getId())
                        .dependsOnTaskId(d.getDependsOnTask().getId())
                        .build())
                .collect(Collectors.toList());

        return WorkflowResponse.builder()
                .id(workflow.getId())
                .name(workflow.getName())
                .description(workflow.getDescription())
                .dagDefinition(workflow.getDagDefinition())
                .cronExpression(workflow.getCronExpression())
                .isActive(workflow.getIsActive())
                .createdAt(workflow.getCreatedAt())
                .tasks(taskDtos)
                .dependencies(depDtos)
                .build();
    }

    public WorkflowRunResponse mapToWorkflowRunResponse(WorkflowRun run) {
        List<TaskRun> taskRuns = taskRunRepository.findByWorkflowRunId(run.getId());
        List<TaskRunResponse> taskRunResponses = taskRuns.stream()
                .map(tr -> TaskRunResponse.builder()
                        .id(tr.getId())
                        .taskId(tr.getTask().getId())
                        .taskName(tr.getTask().getName())
                        .taskType(tr.getTask().getTaskType())
                        .status(tr.getStatus())
                        .attemptNumber(tr.getAttemptNumber())
                        .startedAt(tr.getStartedAt())
                        .finishedAt(tr.getFinishedAt())
                        .output(tr.getOutput())
                        .errorMessage(tr.getErrorMessage())
                        .build())
                .collect(Collectors.toList());

        return WorkflowRunResponse.builder()
                .id(run.getId())
                .workflowId(run.getWorkflow().getId())
                .status(run.getStatus())
                .triggeredAt(run.getTriggeredAt())
                .triggeredBy(run.getTriggeredBy())
                .finishedAt(run.getFinishedAt())
                .taskRuns(taskRunResponses)
                .build();
    }

    private record ParsedDag(List<CreateTaskDto> tasks, List<CreateDependencyDto> dependencies) {}

    private ParsedDag parseDagDefinition(String dagJson) {
        try {
            JsonNode root = objectMapper.readTree(dagJson);
            List<CreateTaskDto> tasks = new ArrayList<>();
            List<CreateDependencyDto> deps = new ArrayList<>();

            if (root.has("nodes")) {
                for (JsonNode node : root.get("nodes")) {
                    UUID id = UUID.fromString(node.get("id").asText());
                    String name = node.has("data") && node.get("data").has("label") ? node.get("data").get("label").asText() : node.get("id").asText();
                    TaskType type = TaskType.SHELL_COMMAND;
                    if (node.has("data") && node.get("data").has("taskType")) {
                        type = TaskType.valueOf(node.get("data").get("taskType").asText());
                    }
                    String config = node.has("data") && node.get("data").has("config") ? node.get("data").get("config").asText() : "{}";

                    tasks.add(CreateTaskDto.builder()
                            .id(id)
                            .name(name)
                            .taskType(type)
                            .config(config)
                            .maxRetries(3)
                            .retryBackoffSeconds(5)
                            .build());
                }
            }

            if (root.has("edges")) {
                for (JsonNode edge : root.get("edges")) {
                    UUID source = UUID.fromString(edge.get("source").asText());
                    UUID target = UUID.fromString(edge.get("target").asText());
                    deps.add(new CreateDependencyDto(target, source)); // target depends on source
                }
            }

            return new ParsedDag(tasks, deps);
        } catch (Exception e) {
            log.warn("Failed to parse dagDefinition JSON string: {}", e.getMessage());
            return new ParsedDag(List.of(), List.of());
        }
    }
}
