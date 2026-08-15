package com.workflow.engine.service.task;

import com.workflow.engine.entity.Task;
import com.workflow.engine.enums.TaskType;

public interface TaskHandler {
    TaskType getSupportedType();
    TaskExecutionResult execute(Task task);
}
