package com.example.service_dependency_tracker.rest;

import com.example.service_dependency_tracker.domain.ServiceNode;
import com.example.service_dependency_tracker.rest.dto.RegisterServiceRequest;
import com.example.service_dependency_tracker.rest.dto.ServiceDTO;
import com.example.service_dependency_tracker.rest.generated.ServicesApi;
import com.example.service_dependency_tracker.service.ServiceManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;

@RestController
public class ServiceController implements ServicesApi {

    private final ServiceManagementService serviceManagementService;

    public ServiceController(ServiceManagementService serviceManagementService) {
        this.serviceManagementService = serviceManagementService;
    }

    @Override
    public ResponseEntity<ServiceDTO> registerService(RegisterServiceRequest registerServiceRequest) {
        ServiceNode node = serviceManagementService.registerService(
                registerServiceRequest.getName(), registerServiceRequest.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(toServiceDTO(node));
    }

    @Override
    public ResponseEntity<List<ServiceDTO>> listServices() {
        List<ServiceDTO> dtos = serviceManagementService.listServices().stream()
                .map(this::toServiceDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<ServiceDTO> getService(String name) {
        return ResponseEntity.ok(toServiceDTO(serviceManagementService.getService(name)));
    }

    @Override
    public ResponseEntity<Void> deleteService(String name) {
        serviceManagementService.deleteService(name);
        return ResponseEntity.noContent().build();
    }

    private ServiceDTO toServiceDTO(ServiceNode node) {
        return new ServiceDTO(
                node.getName(),
                node.getCreatedAt() != null ? node.getCreatedAt().atOffset(ZoneOffset.UTC) : null)
                .description(node.getDescription());
    }
}
