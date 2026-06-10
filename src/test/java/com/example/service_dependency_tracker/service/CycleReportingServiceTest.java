package com.example.service_dependency_tracker.service;

import com.example.service_dependency_tracker.repository.ServiceRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CycleReportingServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @InjectMocks
    private CycleReportingService cycleReportingService;

    @Nested
    class DetectCycles {

        @Test
        void shouldReturnEmptyListWhenNoCyclesReachableFromService() {
            when(serviceRepository.findCyclesFrom("payment-service")).thenReturn(List.of());

            List<List<String>> cycles = cycleReportingService.findCycles("payment-service");

            assertThat(cycles).isEmpty();
        }

        @Test
        void shouldReturnDirectCycleWhenADependsOnBAndBDependsOnA() {
            when(serviceRepository.findCyclesFrom("service-a"))
                    .thenReturn(List.of("service-a,service-b,service-a"));

            List<List<String>> cycles = cycleReportingService.findCycles("service-a");

            assertThat(cycles).hasSize(1);
            assertThat(cycles.get(0)).containsExactly("service-a", "service-b", "service-a");
        }

        @Test
        void shouldReturnTransitiveCycleWhenADependsOnBDependsOnCDependsOnA() {
            when(serviceRepository.findCyclesFrom("service-a"))
                    .thenReturn(List.of("service-a,service-b,service-c,service-a"));

            List<List<String>> cycles = cycleReportingService.findCycles("service-a");

            assertThat(cycles).hasSize(1);
            assertThat(cycles.get(0)).containsExactly("service-a", "service-b", "service-c", "service-a");
        }

        @Test
        void shouldReturnSelfLoopCycleWhenServiceDependsOnItself() {
            when(serviceRepository.findCyclesFrom("service-a"))
                    .thenReturn(List.of("service-a,service-a"));

            List<List<String>> cycles = cycleReportingService.findCycles("service-a");

            assertThat(cycles).hasSize(1);
            assertThat(cycles.get(0)).containsExactly("service-a", "service-a");
        }

        @Test
        void shouldReturnAllDistinctCyclesWhenMultipleCyclesReachable() {
            when(serviceRepository.findCyclesFrom("service-a"))
                    .thenReturn(List.of("service-a,service-b,service-a",
                                        "service-a,service-c,service-d,service-a"));

            List<List<String>> cycles = cycleReportingService.findCycles("service-a");

            assertThat(cycles).hasSize(2);
        }

        @Test
        void shouldReturnEmptyListForAcyclicSubgraph() {
            when(serviceRepository.findCyclesFrom("payment-service")).thenReturn(List.of());

            List<List<String>> cycles = cycleReportingService.findCycles("payment-service");

            assertThat(cycles).isNotNull().isEmpty();
        }
    }
}
