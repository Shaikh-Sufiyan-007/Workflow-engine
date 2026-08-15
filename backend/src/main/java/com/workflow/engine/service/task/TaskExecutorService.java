package com.workflow.engine.service.task;

import com.workflow.engine.entity.Task;
import com.workflow.engine.enums.TaskType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TaskExecutorService {

    private final Map<TaskType, TaskHandler> handlerMap;

    public TaskExecutorService(List<TaskHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(TaskHandler::getSupportedType, Function.identity()));
    }

    public TaskExecutionResult execute(Task task) {
        TaskHandler handler = handlerMap.get(task.getTaskType());
        if (handler == null) {
            return TaskExecutionResult.failure("Unsupported task type: " + task.getTaskType());
        }
        try {
            return handler.execute(task);
        } catch (Exception e) {
            return TaskExecutionResult.failure("Execution failed with exception: " + e.getMessage());
        }
    }
}
