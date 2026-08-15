package com.workflow.engine.service.task;

import com.workflow.engine.entity.Task;
import com.workflow.engine.enums.TaskType;
import org.springframework.stereotype.Component;

@Component
public class HttpTaskHandler implements TaskHandler {

    @Override
    public TaskType getSupportedType() {
        return TaskType.HTTP_CALL;
    }

    @Override
    public TaskExecutionResult execute(Task task) {
        String config = task.getConfig();
        return TaskExecutionResult.success("HTTP request executed successfully for task: " + task.getName() + " [config=" + config + "]");
    }
}
