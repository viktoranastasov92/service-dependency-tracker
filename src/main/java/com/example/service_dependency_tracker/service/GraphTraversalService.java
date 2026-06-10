package com.example.service_dependency_tracker.service;

import com.example.service_dependency_tracker.exception.ServiceNotFoundException;
import com.example.service_dependency_tracker.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GraphTraversalService {

    private final ServiceRepository serviceRepository;
    private final CycleReportingService cycleReportingService;

    @Value("${tracker.traversal.max-depth:50}")
    private int maxDepth = 50;

    public GraphTraversalService(ServiceRepository serviceRepository,
                                  CycleReportingService cycleReportingService) {
        this.serviceRepository = serviceRepository;
        this.cycleReportingService = cycleReportingService;
    }

    public TraversalResult getDownstream(String name) {
        if (!serviceRepository.existsByName(name)) {
            throw new ServiceNotFoundException(name);
        }
        return new TraversalResult(
                name,
                serviceRepository.findAllDownstream(name, maxDepth),
                serviceRepository.findSubgraphEdges(name, maxDepth),
                cycleReportingService.findCycles(name));
    }

    public TraversalResult getUpstream(String name) {
        if (!serviceRepository.existsByName(name)) {
            throw new ServiceNotFoundException(name);
        }
        return new TraversalResult(
                name,
                serviceRepository.findAllUpstream(name, maxDepth),
                serviceRepository.findUpstreamSubgraphEdges(name, maxDepth),
                cycleReportingService.findCycles(name));
    }
}
