package com.workflow.engine.service.task;

import com.workflow.engine.entity.Task;
import com.workflow.engine.enums.TaskType;
import org.springframework.stereotype.Component;

@Component
public class DbQueryTaskHandler implements TaskHandler {

    @Override
    public TaskType getSupportedType() {
        return TaskType.DB_QUERY;
    }

    @Override
    public TaskExecutionResult execute(Task task) {
        return TaskExecutionResult.success("DB Query executed successfully for task: " + task.getName());
    }
}
