package com.example.service_dependency_tracker.service;

import com.example.service_dependency_tracker.domain.DependsOnRelationship;
import com.example.service_dependency_tracker.domain.ServiceNode;
import com.example.service_dependency_tracker.repository.ServiceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServiceManagementService {

    private final ServiceRepository serviceRepository;

    public ServiceManagementService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public ServiceNode registerService(String name, String description) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public ServiceNode getService(String name) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public List<ServiceNode> listServices() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void deleteService(String name) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public DependsOnRelationship addDependency(String fromName, String toName, String dependencyType) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void removeDependency(String fromName, String toName) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public List<DependsOnRelationship> getDirectDependencies(String name) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
