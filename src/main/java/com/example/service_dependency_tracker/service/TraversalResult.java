package com.example.service_dependency_tracker.service;

import com.example.service_dependency_tracker.repository.EdgeQueryResult;
import com.example.service_dependency_tracker.repository.ServiceWithDepthDTO;

import java.util.List;

public record TraversalResult(
        String origin,
        List<ServiceWithDepthDTO> services,
        List<EdgeQueryResult> edges,
        List<List<String>> cycles
) {}
