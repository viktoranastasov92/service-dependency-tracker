package com.example.service_dependency_tracker.exception;

public class DuplicateServiceException extends RuntimeException {

    public DuplicateServiceException(String name) {
        super("Service '" + name + "' is already registered");
    }
}
