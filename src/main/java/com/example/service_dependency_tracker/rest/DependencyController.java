package com.example.service_dependency_tracker.rest;

import com.example.service_dependency_tracker.rest.generated.DependenciesApi;
import com.example.service_dependency_tracker.service.ServiceManagementService;
import com.example.service_dependency_tracker.service.GraphTraversalService;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DependencyController implements DependenciesApi {

    private final ServiceManagementService serviceManagementService;
    private final GraphTraversalService graphTraversalService;

    public DependencyController(ServiceManagementService serviceManagementService,
                                 GraphTraversalService graphTraversalService) {
        this.serviceManagementService = serviceManagementService;
        this.graphTraversalService = graphTraversalService;
    }
}
