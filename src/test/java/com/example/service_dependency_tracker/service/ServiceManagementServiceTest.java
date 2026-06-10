package com.example.service_dependency_tracker.service;

import com.example.service_dependency_tracker.domain.ServiceNode;
import com.example.service_dependency_tracker.exception.DuplicateServiceException;
import com.example.service_dependency_tracker.exception.ServiceNotFoundException;
import com.example.service_dependency_tracker.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceManagementServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @InjectMocks
    private ServiceManagementService serviceManagementService;

    // ---------------------------------------------------------------------------
    // registerService
    // ---------------------------------------------------------------------------

    @Nested
    class RegisterService {

        @Test
        void shouldReturnPersistedServiceWhenNameIsUnique() {
            ServiceNode saved = nodeWithName("payment-service");
            when(serviceRepository.existsByName("payment-service")).thenReturn(false);
            when(serviceRepository.save(any(ServiceNode.class))).thenReturn(saved);

            ServiceNode result = serviceManagementService.registerService("payment-service", "Handles payments");

            assertThat(result.getName()).isEqualTo("payment-service");
        }

        @Test
        void shouldPersistDescriptionWhenProvided() {
            ServiceNode saved = nodeWithNameAndDescription("payment-service", "Handles payments");
            when(serviceRepository.existsByName(anyString())).thenReturn(false);
            when(serviceRepository.save(any(ServiceNode.class))).thenReturn(saved);

            serviceManagementService.registerService("payment-service", "Handles payments");

            ArgumentCaptor<ServiceNode> captor = ArgumentCaptor.forClass(ServiceNode.class);
            verify(serviceRepository).save(captor.capture());
            assertThat(captor.getValue().getDescription()).isEqualTo("Handles payments");
        }

        @Test
        void shouldPersistNullDescriptionWhenNotProvided() {
            when(serviceRepository.existsByName(anyString())).thenReturn(false);
            when(serviceRepository.save(any(ServiceNode.class))).thenAnswer(inv -> inv.getArgument(0));

            serviceManagementService.registerService("payment-service", null);

            ArgumentCaptor<ServiceNode> captor = ArgumentCaptor.forClass(ServiceNode.class);
            verify(serviceRepository).save(captor.capture());
            assertThat(captor.getValue().getDescription()).isNull();
        }

        @Test
        void shouldSetCreatedAtBeforePersisting() {
            Instant before = Instant.now();
            when(serviceRepository.existsByName(anyString())).thenReturn(false);
            when(serviceRepository.save(any(ServiceNode.class))).thenAnswer(inv -> inv.getArgument(0));

            serviceManagementService.registerService("payment-service", null);

            ArgumentCaptor<ServiceNode> captor = ArgumentCaptor.forClass(ServiceNode.class);
            verify(serviceRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedAt()).isNotNull().isAfterOrEqualTo(before);
        }

        @Test
        void shouldThrowDuplicateServiceExceptionWhenNameAlreadyExists() {
            when(serviceRepository.existsByName("payment-service")).thenReturn(true);

            assertThatThrownBy(() -> serviceManagementService.registerService("payment-service", null))
                    .isInstanceOf(DuplicateServiceException.class)
                    .hasMessageContaining("payment-service");
        }

        @Test
        void shouldNotPersistWhenNameAlreadyExists() {
            when(serviceRepository.existsByName("payment-service")).thenReturn(true);

            try {
                serviceManagementService.registerService("payment-service", null);
            } catch (DuplicateServiceException ignored) {}

            verify(serviceRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // getService
    // ---------------------------------------------------------------------------

    @Nested
    class GetService {

        @Test
        void shouldReturnServiceWhenFound() {
            ServiceNode node = nodeWithName("payment-service");
            when(serviceRepository.findByName("payment-service")).thenReturn(Optional.of(node));

            ServiceNode result = serviceManagementService.getService("payment-service");

            assertThat(result.getName()).isEqualTo("payment-service");
        }

        @Test
        void shouldThrowServiceNotFoundExceptionWhenNameDoesNotExist() {
            when(serviceRepository.findByName("unknown-service")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> serviceManagementService.getService("unknown-service"))
                    .isInstanceOf(ServiceNotFoundException.class)
                    .hasMessageContaining("unknown-service");
        }
    }

    // ---------------------------------------------------------------------------
    // listServices
    // ---------------------------------------------------------------------------

    @Nested
    class ListServices {

        @Test
        void shouldReturnAllRegisteredServices() {
            when(serviceRepository.findAll()).thenReturn(
                    List.of(nodeWithName("payment-service"), nodeWithName("user-auth-service")));

            List<ServiceNode> result = serviceManagementService.listServices();

            assertThat(result).hasSize(2)
                    .extracting(ServiceNode::getName)
                    .containsExactlyInAnyOrder("payment-service", "user-auth-service");
        }

        @Test
        void shouldReturnEmptyListWhenNoServicesRegistered() {
            when(serviceRepository.findAll()).thenReturn(List.of());

            List<ServiceNode> result = serviceManagementService.listServices();

            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------
    // deleteService
    // ---------------------------------------------------------------------------

    @Nested
    class DeleteService {

        @Test
        void shouldDeleteServiceWhenFound() {
            ServiceNode node = nodeWithName("payment-service");
            node.setId(1L);
            when(serviceRepository.findByName("payment-service")).thenReturn(Optional.of(node));

            serviceManagementService.deleteService("payment-service");

            verify(serviceRepository).deleteById(1L);
        }

        @Test
        void shouldThrowServiceNotFoundExceptionWhenServiceDoesNotExist() {
            when(serviceRepository.findByName("unknown-service")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> serviceManagementService.deleteService("unknown-service"))
                    .isInstanceOf(ServiceNotFoundException.class)
                    .hasMessageContaining("unknown-service");
        }

        @Test
        void shouldNotCallDeleteWhenServiceDoesNotExist() {
            when(serviceRepository.findByName("unknown-service")).thenReturn(Optional.empty());

            try {
                serviceManagementService.deleteService("unknown-service");
            } catch (ServiceNotFoundException ignored) {}

            verify(serviceRepository, never()).deleteById(any());
        }
    }

    // ---------------------------------------------------------------------------
    // addDependency
    // ---------------------------------------------------------------------------

    @Nested
    class AddDependency {

        @Test
        void shouldAddDependencyEdgeFromSourceToTarget() {
            ServiceNode source = nodeWithName("payment-service");
            ServiceNode target = nodeWithName("user-auth-service");
            when(serviceRepository.findByName("payment-service")).thenReturn(Optional.of(source));
            when(serviceRepository.findByName("user-auth-service")).thenReturn(Optional.of(target));
            when(serviceRepository.save(any(ServiceNode.class))).thenAnswer(inv -> inv.getArgument(0));

            serviceManagementService.addDependency("payment-service", "user-auth-service", "RUNTIME");

            ArgumentCaptor<ServiceNode> captor = ArgumentCaptor.forClass(ServiceNode.class);
            verify(serviceRepository).save(captor.capture());
            assertThat(captor.getValue().getDependsOn())
                    .hasSize(1)
                    .first()
                    .satisfies(rel -> {
                        assertThat(rel.getTarget().getName()).isEqualTo("user-auth-service");
                        assertThat(rel.getDependencyType()).isEqualTo("RUNTIME");
                    });
        }

        @Test
        void shouldThrowServiceNotFoundExceptionWhenSourceDoesNotExist() {
            when(serviceRepository.findByName("unknown-service")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    serviceManagementService.addDependency("unknown-service", "user-auth-service", "RUNTIME"))
                    .isInstanceOf(ServiceNotFoundException.class)
                    .hasMessageContaining("unknown-service");
        }

        @Test
        void shouldThrowServiceNotFoundExceptionWhenTargetDoesNotExist() {
            ServiceNode source = nodeWithName("payment-service");
            when(serviceRepository.findByName("payment-service")).thenReturn(Optional.of(source));
            when(serviceRepository.findByName("unknown-service")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    serviceManagementService.addDependency("payment-service", "unknown-service", "RUNTIME"))
                    .isInstanceOf(ServiceNotFoundException.class)
                    .hasMessageContaining("unknown-service");
        }

        @Test
        void shouldThrowDuplicateDependencyExceptionWhenEdgeAlreadyExists() {
            ServiceNode target = nodeWithName("user-auth-service");
            ServiceNode source = nodeWithName("payment-service");
            source.getDependsOn().add(new com.example.service_dependency_tracker.domain.DependsOnRelationship(target, "RUNTIME"));
            when(serviceRepository.findByName("payment-service")).thenReturn(Optional.of(source));
            when(serviceRepository.findByName("user-auth-service")).thenReturn(Optional.of(target));

            assertThatThrownBy(() ->
                    serviceManagementService.addDependency("payment-service", "user-auth-service", "RUNTIME"))
                    .isInstanceOf(DuplicateServiceException.class);
        }

        @Test
        void shouldAllowCyclicDependencyWithoutThrowing() {
            // B→A already exists; adding A→B creates cycle A→B→A — must not throw (ADR-006)
            ServiceNode nodeA = nodeWithName("service-a");
            ServiceNode nodeB = nodeWithName("service-b");
            nodeB.getDependsOn().add(new com.example.service_dependency_tracker.domain.DependsOnRelationship(nodeA, "RUNTIME"));
            when(serviceRepository.findByName("service-a")).thenReturn(Optional.of(nodeA));
            when(serviceRepository.findByName("service-b")).thenReturn(Optional.of(nodeB));
            when(serviceRepository.save(any(ServiceNode.class))).thenAnswer(inv -> inv.getArgument(0));

            serviceManagementService.addDependency("service-a", "service-b", "RUNTIME");
        }
    }

    // ---------------------------------------------------------------------------
    // removeDependency
    // ---------------------------------------------------------------------------

    @Nested
    class RemoveDependency {

        @Test
        void shouldRemoveDependencyEdgeWhenItExists() {
            ServiceNode target = nodeWithName("user-auth-service");
            ServiceNode source = nodeWithName("payment-service");
            source.getDependsOn().add(new com.example.service_dependency_tracker.domain.DependsOnRelationship(target, "RUNTIME"));
            when(serviceRepository.findByName("payment-service")).thenReturn(Optional.of(source));
            when(serviceRepository.save(any(ServiceNode.class))).thenAnswer(inv -> inv.getArgument(0));

            serviceManagementService.removeDependency("payment-service", "user-auth-service");

            ArgumentCaptor<ServiceNode> captor = ArgumentCaptor.forClass(ServiceNode.class);
            verify(serviceRepository).save(captor.capture());
            assertThat(captor.getValue().getDependsOn()).isEmpty();
        }

        @Test
        void shouldThrowDependencyNotFoundExceptionWhenEdgeDoesNotExist() {
            ServiceNode source = nodeWithName("payment-service");
            when(serviceRepository.findByName("payment-service")).thenReturn(Optional.of(source));

            assertThatThrownBy(() ->
                    serviceManagementService.removeDependency("payment-service", "nonexistent-service"))
                    .isInstanceOf(com.example.service_dependency_tracker.exception.DependencyNotFoundException.class)
                    .hasMessageContaining("payment-service")
                    .hasMessageContaining("nonexistent-service");
        }

        @Test
        void shouldThrowServiceNotFoundExceptionWhenSourceDoesNotExist() {
            when(serviceRepository.findByName("unknown-service")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    serviceManagementService.removeDependency("unknown-service", "user-auth-service"))
                    .isInstanceOf(ServiceNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------------------
    // getDirectDependencies
    // ---------------------------------------------------------------------------

    @Nested
    class GetDirectDependencies {

        @Test
        void shouldReturnDirectDependenciesOfService() {
            ServiceNode target1 = nodeWithName("user-auth-service");
            ServiceNode target2 = nodeWithName("postgres-primary");
            ServiceNode source = nodeWithName("payment-service");
            source.getDependsOn().add(new com.example.service_dependency_tracker.domain.DependsOnRelationship(target1, "RUNTIME"));
            source.getDependsOn().add(new com.example.service_dependency_tracker.domain.DependsOnRelationship(target2, "RUNTIME"));
            when(serviceRepository.findByName("payment-service")).thenReturn(Optional.of(source));

            List<com.example.service_dependency_tracker.domain.DependsOnRelationship> result =
                    serviceManagementService.getDirectDependencies("payment-service");

            assertThat(result).hasSize(2)
                    .extracting(r -> r.getTarget().getName())
                    .containsExactlyInAnyOrder("user-auth-service", "postgres-primary");
        }

        @Test
        void shouldReturnEmptyListWhenServiceHasNoDependencies() {
            when(serviceRepository.findByName("payment-service"))
                    .thenReturn(Optional.of(nodeWithName("payment-service")));

            List<com.example.service_dependency_tracker.domain.DependsOnRelationship> result =
                    serviceManagementService.getDirectDependencies("payment-service");

            assertThat(result).isEmpty();
        }

        @Test
        void shouldThrowServiceNotFoundExceptionWhenServiceDoesNotExist() {
            when(serviceRepository.findByName("unknown-service")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    serviceManagementService.getDirectDependencies("unknown-service"))
                    .isInstanceOf(ServiceNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------

    private ServiceNode nodeWithName(String name) {
        ServiceNode node = new ServiceNode(name, null);
        return node;
    }

    private ServiceNode nodeWithNameAndDescription(String name, String description) {
        return new ServiceNode(name, description);
    }
}
