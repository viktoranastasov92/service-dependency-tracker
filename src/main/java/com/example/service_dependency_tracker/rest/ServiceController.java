package com.example.service_dependency_tracker.rest;

import com.example.service_dependency_tracker.rest.generated.ServicesApi;
import com.example.service_dependency_tracker.service.ServiceManagementService;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceController implements ServicesApi {

    private final ServiceManagementService serviceManagementService;

    public ServiceController(ServiceManagementService serviceManagementService) {
        this.serviceManagementService = serviceManagementService;
    }
}
