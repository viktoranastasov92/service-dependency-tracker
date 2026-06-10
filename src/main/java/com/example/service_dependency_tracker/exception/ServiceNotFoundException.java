package com.example.service_dependency_tracker.exception;

public class ServiceNotFoundException extends RuntimeException {

    public ServiceNotFoundException(String name) {
        super("Service '" + name + "' not found");
    }
}
