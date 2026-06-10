package com.example.service_dependency_tracker.rest;

import com.example.service_dependency_tracker.exception.ServiceNotFoundException;
import com.example.service_dependency_tracker.repository.EdgeQueryResult;
import com.example.service_dependency_tracker.repository.ServiceWithDepthDTO;
import com.example.service_dependency_tracker.service.GraphTraversalService;
import com.example.service_dependency_tracker.service.TraversalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class GraphControllerTest {

    @Mock
    private GraphTraversalService graphTraversalService;

    @InjectMocks
    private GraphController graphController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(graphController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ---------------------------------------------------------------------------
    // GET /services/{name}/downstream
    // ---------------------------------------------------------------------------

    @Nested
    class GetDownstream {

        @Test
        void shouldReturn200WithTraversalResultContainingOriginServicesEdgesAndCycles() throws Exception {
            TraversalResult result = new TraversalResult(
                    "payment-service",
                    List.of(new ServiceWithDepthDTO("user-auth-service", null, 1, null)),
                    List.of(new EdgeQueryResult("payment-service", "user-auth-service", "RUNTIME")),
                    List.of());
            when(graphTraversalService.getDownstream("payment-service")).thenReturn(result);

            mockMvc.perform(get("/services/payment-service/downstream"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.origin").value("payment-service"))
                    .andExpect(jsonPath("$.services", hasSize(1)))
                    .andExpect(jsonPath("$.services[0].name").value("user-auth-service"))
                    .andExpect(jsonPath("$.services[0].depth").value(1))
                    .andExpect(jsonPath("$.edges", hasSize(1)))
                    .andExpect(jsonPath("$.cycles").isArray())
                    .andExpect(jsonPath("$.cycles", empty()));
        }

        @Test
        void shouldReturn200WithEmptyServicesWhenServiceHasNoDependencies() throws Exception {
            TraversalResult result = new TraversalResult("payment-service", List.of(), List.of(), List.of());
            when(graphTraversalService.getDownstream("payment-service")).thenReturn(result);

            mockMvc.perform(get("/services/payment-service/downstream"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.services", empty()))
                    .andExpect(jsonPath("$.edges", empty()))
                    .andExpect(jsonPath("$.cycles", empty()));
        }

        @Test
        void shouldReturn200WithPopulatedCyclesFieldWhenCycleDetected() throws Exception {
            List<String> cycle = List.of("payment-service", "fraud-detection-service", "payment-service");
            TraversalResult result = new TraversalResult(
                    "payment-service",
                    List.of(new ServiceWithDepthDTO("fraud-detection-service", null, 1, null)),
                    List.of(),
                    List.of(cycle));
            when(graphTraversalService.getDownstream("payment-service")).thenReturn(result);

            mockMvc.perform(get("/services/payment-service/downstream"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cycles", hasSize(1)))
                    .andExpect(jsonPath("$.cycles[0]",
                            contains("payment-service", "fraud-detection-service", "payment-service")));
        }

        @Test
        void shouldReturn404WhenServiceDoesNotExist() throws Exception {
            when(graphTraversalService.getDownstream("unknown-service"))
                    .thenThrow(new ServiceNotFoundException("unknown-service"));

            mockMvc.perform(get("/services/unknown-service/downstream"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("unknown-service")));
        }
    }

    // ---------------------------------------------------------------------------
    // GET /services/{name}/upstream
    // ---------------------------------------------------------------------------

    @Nested
    class GetUpstream {

        @Test
        void shouldReturn200WithUpstreamServicesRepresentingBlastRadius() throws Exception {
            TraversalResult result = new TraversalResult(
                    "postgres-primary",
                    List.of(
                            new ServiceWithDepthDTO("user-auth-service", null, 1, null),
                            new ServiceWithDepthDTO("payment-service", null, 2, null)),
                    List.of(),
                    List.of());
            when(graphTraversalService.getUpstream("postgres-primary")).thenReturn(result);

            mockMvc.perform(get("/services/postgres-primary/upstream"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.origin").value("postgres-primary"))
                    .andExpect(jsonPath("$.services", hasSize(2)))
                    .andExpect(jsonPath("$.cycles", empty()));
        }

        @Test
        void shouldReturn200WithEmptyServicesWhenNothingDependsOnService() throws Exception {
            TraversalResult result = new TraversalResult("postgres-primary", List.of(), List.of(), List.of());
            when(graphTraversalService.getUpstream("postgres-primary")).thenReturn(result);

            mockMvc.perform(get("/services/postgres-primary/upstream"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.services", empty()));
        }

        @Test
        void shouldReturn404WhenServiceDoesNotExist() throws Exception {
            when(graphTraversalService.getUpstream("unknown-service"))
                    .thenThrow(new ServiceNotFoundException("unknown-service"));

            mockMvc.perform(get("/services/unknown-service/upstream"))
                    .andExpect(status().isNotFound());
        }
    }
}
