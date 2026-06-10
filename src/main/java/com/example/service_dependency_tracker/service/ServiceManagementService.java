package com.example.service_dependency_tracker.service;

import com.example.service_dependency_tracker.domain.DependsOnRelationship;
import com.example.service_dependency_tracker.domain.ServiceNode;
import com.example.service_dependency_tracker.exception.DependencyNotFoundException;
import com.example.service_dependency_tracker.exception.DuplicateServiceException;
import com.example.service_dependency_tracker.exception.ServiceNotFoundException;
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
        String normalized = name.toLowerCase();
        if (serviceRepository.existsByName(normalized)) {
            throw new DuplicateServiceException(normalized);
        }
        return serviceRepository.save(new ServiceNode(normalized, description));
    }

    public ServiceNode getService(String name) {
        return serviceRepository.findByName(name)
                .orElseThrow(() -> new ServiceNotFoundException(name));
    }

    public List<ServiceNode> listServices() {
        return serviceRepository.findAll();
    }

    public void deleteService(String name) {
        ServiceNode node = serviceRepository.findByName(name)
                .orElseThrow(() -> new ServiceNotFoundException(name));
        serviceRepository.deleteById(node.getId());
    }

    public DependsOnRelationship addDependency(String fromName, String toName, String dependencyType) {
        ServiceNode source = serviceRepository.findByName(fromName)
                .orElseThrow(() -> new ServiceNotFoundException(fromName));
        ServiceNode target = serviceRepository.findByName(toName)
                .orElseThrow(() -> new ServiceNotFoundException(toName));

        boolean alreadyExists = source.getDependsOn().stream()
                .anyMatch(rel -> rel.getTarget().getName().equals(toName));
        if (alreadyExists) {
            throw new DuplicateServiceException(fromName + " → " + toName);
        }

        String type = dependencyType != null ? dependencyType : "RUNTIME";
        DependsOnRelationship rel = new DependsOnRelationship(target, type);
        source.getDependsOn().add(rel);
        serviceRepository.save(source);
        return rel;
    }

    public void removeDependency(String fromName, String toName) {
        ServiceNode source = serviceRepository.findByName(fromName)
                .orElseThrow(() -> new ServiceNotFoundException(fromName));

        DependsOnRelationship rel = source.getDependsOn().stream()
                .filter(r -> r.getTarget().getName().equals(toName))
                .findFirst()
                .orElseThrow(() -> new DependencyNotFoundException(fromName, toName));

        source.getDependsOn().remove(rel);
        serviceRepository.save(source);
    }

    public List<DependsOnRelationship> getDirectDependencies(String name) {
        return serviceRepository.findByName(name)
                .orElseThrow(() -> new ServiceNotFoundException(name))
                .getDependsOn();
    }
}
