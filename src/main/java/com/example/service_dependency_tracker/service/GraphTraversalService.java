package com.example.service_dependency_tracker.service;

import com.example.service_dependency_tracker.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GraphTraversalService {

    private final ServiceRepository serviceRepository;
    private final CycleReportingService cycleReportingService;

    @Value("${tracker.traversal.max-depth:50}")
    private int maxDepth;

    public GraphTraversalService(ServiceRepository serviceRepository,
                                  CycleReportingService cycleReportingService) {
        this.serviceRepository = serviceRepository;
        this.cycleReportingService = cycleReportingService;
    }
}
