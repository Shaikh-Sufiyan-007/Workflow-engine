package com.workflow.engine.exception;

import java.util.List;

public class CircularDependencyException extends RuntimeException {

    private final List<String> cyclePath;

    public CircularDependencyException(String message) {
        super(message);
        this.cyclePath = List.of();
    }

    public CircularDependencyException(String message, List<String> cyclePath) {
        super(message + (cyclePath != null && !cyclePath.isEmpty() ? " Path: " + String.join(" -> ", cyclePath) : ""));
        this.cyclePath = cyclePath != null ? cyclePath : List.of();
    }

    public List<String> getCyclePath() {
        return cyclePath;
    }
}
