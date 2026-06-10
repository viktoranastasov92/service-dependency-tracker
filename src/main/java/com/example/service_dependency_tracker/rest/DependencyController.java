package com.example.service_dependency_tracker.rest;

import com.example.service_dependency_tracker.domain.DependsOnRelationship;
import com.example.service_dependency_tracker.rest.dto.AddDependencyRequest;
import com.example.service_dependency_tracker.rest.dto.DependencyDTO;
import com.example.service_dependency_tracker.rest.dto.DependencyType;
import com.example.service_dependency_tracker.rest.generated.DependenciesApi;
import com.example.service_dependency_tracker.service.GraphTraversalService;
import com.example.service_dependency_tracker.service.ServiceManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;

@RestController
public class DependencyController implements DependenciesApi {

    private final ServiceManagementService serviceManagementService;
    private final GraphTraversalService graphTraversalService;

    public DependencyController(ServiceManagementService serviceManagementService,
                                 GraphTraversalService graphTraversalService) {
        this.serviceManagementService = serviceManagementService;
        this.graphTraversalService = graphTraversalService;
    }

    @Override
    public ResponseEntity<DependencyDTO> addDependency(String name, AddDependencyRequest addDependencyRequest) {
        String depType = addDependencyRequest.getDependencyType() != null
                ? addDependencyRequest.getDependencyType().getValue()
                : "RUNTIME";
        DependsOnRelationship rel = serviceManagementService.addDependency(
                name, addDependencyRequest.getDependsOnName(), depType);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDependencyDTO(name, rel));
    }

    @Override
    public ResponseEntity<List<DependencyDTO>> getDirectDependencies(String name) {
        List<DependencyDTO> dtos = serviceManagementService.getDirectDependencies(name).stream()
                .map(rel -> toDependencyDTO(name, rel))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<Void> removeDependency(String name, String depName) {
        serviceManagementService.removeDependency(name, depName);
        return ResponseEntity.noContent().build();
    }

    private DependencyDTO toDependencyDTO(String fromName, DependsOnRelationship rel) {
        return new DependencyDTO(
                fromName,
                rel.getTarget().getName(),
                DependencyType.fromValue(rel.getDependencyType()),
                rel.getCreatedAt() != null ? rel.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
    }
}
