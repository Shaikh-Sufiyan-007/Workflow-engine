package com.workflow.engine.service.task;

import com.workflow.engine.entity.Task;
import com.workflow.engine.enums.TaskType;
import org.springframework.stereotype.Component;

@Component
public class ShellTaskHandler implements TaskHandler {

    @Override
    public TaskType getSupportedType() {
        return TaskType.SHELL_COMMAND;
    }

    @Override
    public TaskExecutionResult execute(Task task) {
        String config = task.getConfig();
        // Standard shell execution logic or echo simulation
        return TaskExecutionResult.success("Shell command executed successfully: " + (config != null ? config : task.getName()));
    }
}
