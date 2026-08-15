package com.workflow.engine.service.task;

import com.workflow.engine.entity.Task;
import com.workflow.engine.enums.TaskType;
import org.springframework.stereotype.Component;

@Component
public class EmailTaskHandler implements TaskHandler {

    @Override
    public TaskType getSupportedType() {
        return TaskType.EMAIL;
    }

    @Override
    public TaskExecutionResult execute(Task task) {
        return TaskExecutionResult.success("Email sent successfully for task: " + task.getName());
    }
}
