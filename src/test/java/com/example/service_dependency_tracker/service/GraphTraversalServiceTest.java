package com.example.service_dependency_tracker.service;

import com.example.service_dependency_tracker.domain.ServiceNode;
import com.example.service_dependency_tracker.exception.ServiceNotFoundException;
import com.example.service_dependency_tracker.repository.ServiceRepository;
import com.example.service_dependency_tracker.repository.ServiceWithDepthDTO;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphTraversalServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private CycleReportingService cycleReportingService;

    @InjectMocks
    private GraphTraversalService graphTraversalService;

    // ---------------------------------------------------------------------------
    // getDownstream
    // ---------------------------------------------------------------------------

    @Nested
    class GetDownstream {

        @Test
        void shouldReturnEmptyServicesWhenServiceHasNoDependencies() {
            when(serviceRepository.existsByName("payment-service")).thenReturn(true);
            when(serviceRepository.findAllDownstream("payment-service", 50)).thenReturn(List.of());
            when(serviceRepository.findSubgraphEdges("payment-service", 50)).thenReturn(List.of());
            when(cycleReportingService.findCycles("payment-service")).thenReturn(List.of());

            var result = graphTraversalService.getDownstream("payment-service");

            assertThat(result.services()).isEmpty();
            assertThat(result.edges()).isEmpty();
            assertThat(result.cycles()).isEmpty();
            assertThat(result.origin()).isEqualTo("payment-service");
        }

        @Test
        void shouldReturnDownstreamServicesWithCorrectDepths() {
            when(serviceRepository.existsByName("payment-service")).thenReturn(true);
            when(serviceRepository.findAllDownstream("payment-service", 50))
                    .thenReturn(List.of(
                            stubDepthDTO("user-auth-service", 1),
                            stubDepthDTO("postgres-primary", 2)));
            when(serviceRepository.findSubgraphEdges("payment-service", 50)).thenReturn(List.of());
            when(cycleReportingService.findCycles("payment-service")).thenReturn(List.of());

            var result = graphTraversalService.getDownstream("payment-service");

            assertThat(result.services()).hasSize(2);
            assertThat(result.services()).extracting(ServiceWithDepthDTO::name)
                    .containsExactlyInAnyOrder("user-auth-service", "postgres-primary");
        }

        @Test
        void shouldPopulateCyclesFieldWhenCyclesDetected() {
            List<String> cycle = List.of("payment-service", "fraud-detection-service", "payment-service");
            when(serviceRepository.existsByName("payment-service")).thenReturn(true);
            when(serviceRepository.findAllDownstream("payment-service", 50)).thenReturn(List.of());
            when(serviceRepository.findSubgraphEdges("payment-service", 50)).thenReturn(List.of());
            when(cycleReportingService.findCycles("payment-service")).thenReturn(List.of(cycle));

            var result = graphTraversalService.getDownstream("payment-service");

            assertThat(result.cycles()).hasSize(1);
            assertThat(result.cycles().get(0))
                    .containsExactly("payment-service", "fraud-detection-service", "payment-service");
        }

        @Test
        void shouldReturnEmptyCyclesFieldWhenNoDetectedCycles() {
            when(serviceRepository.existsByName("payment-service")).thenReturn(true);
            when(serviceRepository.findAllDownstream(anyString(), anyInt())).thenReturn(List.of());
            when(serviceRepository.findSubgraphEdges(anyString(), anyInt())).thenReturn(List.of());
            when(cycleReportingService.findCycles("payment-service")).thenReturn(List.of());

            var result = graphTraversalService.getDownstream("payment-service");

            assertThat(result.cycles()).isNotNull().isEmpty();
        }

        @Test
        void shouldThrowServiceNotFoundExceptionWhenServiceDoesNotExist() {
            when(serviceRepository.existsByName("unknown-service")).thenReturn(false);

            assertThatThrownBy(() -> graphTraversalService.getDownstream("unknown-service"))
                    .isInstanceOf(ServiceNotFoundException.class)
                    .hasMessageContaining("unknown-service");
        }
    }

    // ---------------------------------------------------------------------------
    // getUpstream
    // ---------------------------------------------------------------------------

    @Nested
    class GetUpstream {

        @Test
        void shouldReturnEmptyServicesWhenNothingDependsOnService() {
            when(serviceRepository.existsByName("postgres-primary")).thenReturn(true);
            when(serviceRepository.findAllUpstream("postgres-primary", 50)).thenReturn(List.of());
            when(serviceRepository.findUpstreamSubgraphEdges("postgres-primary", 50)).thenReturn(List.of());
            when(cycleReportingService.findCycles("postgres-primary")).thenReturn(List.of());

            var result = graphTraversalService.getUpstream("postgres-primary");

            assertThat(result.services()).isEmpty();
            assertThat(result.origin()).isEqualTo("postgres-primary");
        }

        @Test
        void shouldReturnUpstreamServicesWithCorrectDepths() {
            when(serviceRepository.existsByName("postgres-primary")).thenReturn(true);
            when(serviceRepository.findAllUpstream("postgres-primary", 50))
                    .thenReturn(List.of(
                            stubDepthDTO("user-auth-service", 1),
                            stubDepthDTO("payment-service", 2)));
            when(serviceRepository.findUpstreamSubgraphEdges("postgres-primary", 50)).thenReturn(List.of());
            when(cycleReportingService.findCycles("postgres-primary")).thenReturn(List.of());

            var result = graphTraversalService.getUpstream("postgres-primary");

            assertThat(result.services()).hasSize(2);
        }

        @Test
        void shouldThrowServiceNotFoundExceptionWhenServiceDoesNotExist() {
            when(serviceRepository.existsByName("unknown-service")).thenReturn(false);

            assertThatThrownBy(() -> graphTraversalService.getUpstream("unknown-service"))
                    .isInstanceOf(ServiceNotFoundException.class)
                    .hasMessageContaining("unknown-service");
        }
    }

    // ---------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------

    private ServiceWithDepthDTO stubDepthDTO(String name, int depth) {
        return new ServiceWithDepthDTO(name, null, depth, null);
    }
}
