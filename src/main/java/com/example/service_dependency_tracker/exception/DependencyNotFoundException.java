package com.example.service_dependency_tracker.exception;

public class DependencyNotFoundException extends RuntimeException {

    public DependencyNotFoundException(String fromName, String toName) {
        super("Dependency from '" + fromName + "' to '" + toName + "' not found");
    }
}
