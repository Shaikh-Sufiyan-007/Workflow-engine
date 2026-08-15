package com.workflow.engine.service.task;

public record TaskExecutionResult(
        boolean success,
        String output,
        String errorMessage
) {
    public static TaskExecutionResult success(String output) {
        return new TaskExecutionResult(true, output, null);
    }

    public static TaskExecutionResult failure(String errorMessage) {
        return new TaskExecutionResult(false, null, errorMessage);
    }
}
