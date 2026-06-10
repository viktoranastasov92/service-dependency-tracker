package com.example.service_dependency_tracker.service;

import com.example.service_dependency_tracker.repository.ServiceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CycleReportingService {

    private final ServiceRepository serviceRepository;

    public CycleReportingService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public List<List<String>> findCycles(String name) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
