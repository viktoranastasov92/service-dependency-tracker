package com.example.service_dependency_tracker.rest;

import com.example.service_dependency_tracker.domain.DependsOnRelationship;
import com.example.service_dependency_tracker.domain.ServiceNode;
import com.example.service_dependency_tracker.exception.DependencyNotFoundException;
import com.example.service_dependency_tracker.exception.DuplicateServiceException;
import com.example.service_dependency_tracker.exception.ServiceNotFoundException;
import com.example.service_dependency_tracker.service.GraphTraversalService;
import com.example.service_dependency_tracker.service.ServiceManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DependencyControllerTest {

    @Mock
    private ServiceManagementService serviceManagementService;

    @Mock
    private GraphTraversalService graphTraversalService;

    @InjectMocks
    private DependencyController dependencyController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(dependencyController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ---------------------------------------------------------------------------
    // POST /services/{name}/dependencies
    // ---------------------------------------------------------------------------

    @Nested
    class AddDependency {

        @Test
        void shouldReturn201WithDependencyDTOWhenEdgeAddedSuccessfully() throws Exception {
            ServiceNode target = serviceNode("user-auth-service");
            DependsOnRelationship rel = new DependsOnRelationship(target, "RUNTIME");
            when(serviceManagementService.addDependency("payment-service", "user-auth-service", "RUNTIME"))
                    .thenReturn(rel);

            mockMvc.perform(post("/services/payment-service/dependencies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"dependsOnName":"user-auth-service","dependencyType":"RUNTIME"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fromService").value("payment-service"))
                    .andExpect(jsonPath("$.toService").value("user-auth-service"))
                    .andExpect(jsonPath("$.dependencyType").value("RUNTIME"));
        }

        @Test
        void shouldReturn400WhenDependsOnNameIsAbsent() throws Exception {
            mockMvc.perform(post("/services/payment-service/dependencies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"dependencyType":"RUNTIME"}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn404WhenSourceServiceDoesNotExist() throws Exception {
            when(serviceManagementService.addDependency("unknown-service", "user-auth-service", "RUNTIME"))
                    .thenThrow(new ServiceNotFoundException("unknown-service"));

            mockMvc.perform(post("/services/unknown-service/dependencies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"dependsOnName":"user-auth-service","dependencyType":"RUNTIME"}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void shouldReturn404WhenTargetServiceDoesNotExist() throws Exception {
            when(serviceManagementService.addDependency("payment-service", "unknown-service", "RUNTIME"))
                    .thenThrow(new ServiceNotFoundException("unknown-service"));

            mockMvc.perform(post("/services/payment-service/dependencies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"dependsOnName":"unknown-service","dependencyType":"RUNTIME"}
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn409WhenDependencyAlreadyExists() throws Exception {
            when(serviceManagementService.addDependency("payment-service", "user-auth-service", "RUNTIME"))
                    .thenThrow(new DuplicateServiceException("payment-service → user-auth-service"));

            mockMvc.perform(post("/services/payment-service/dependencies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"dependsOnName":"user-auth-service","dependencyType":"RUNTIME"}
                                    """))
                    .andExpect(status().isConflict());
        }

        @Test
        void shouldReturn201AndStoreCyclicDependencyWithoutError() throws Exception {
            // A→B already exists; B→A creates a cycle — cycles are allowed (ADR-006)
            ServiceNode target = serviceNode("service-a");
            DependsOnRelationship rel = new DependsOnRelationship(target, "RUNTIME");
            when(serviceManagementService.addDependency("service-b", "service-a", "RUNTIME"))
                    .thenReturn(rel);

            mockMvc.perform(post("/services/service-b/dependencies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"dependsOnName":"service-a","dependencyType":"RUNTIME"}
                                    """))
                    .andExpect(status().isCreated());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /services/{name}/dependencies
    // ---------------------------------------------------------------------------

    @Nested
    class GetDirectDependencies {

        @Test
        void shouldReturn200WithDirectDependenciesList() throws Exception {
            ServiceNode t1 = serviceNode("user-auth-service");
            ServiceNode t2 = serviceNode("postgres-primary");
            when(serviceManagementService.getDirectDependencies("payment-service"))
                    .thenReturn(List.of(
                            new DependsOnRelationship(t1, "RUNTIME"),
                            new DependsOnRelationship(t2, "RUNTIME")));

            mockMvc.perform(get("/services/payment-service/dependencies"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].toService",
                            containsInAnyOrder("user-auth-service", "postgres-primary")));
        }

        @Test
        void shouldReturn200WithEmptyArrayWhenServiceHasNoDependencies() throws Exception {
            when(serviceManagementService.getDirectDependencies("payment-service"))
                    .thenReturn(List.of());

            mockMvc.perform(get("/services/payment-service/dependencies"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void shouldReturn404WhenServiceDoesNotExist() throws Exception {
            when(serviceManagementService.getDirectDependencies("unknown-service"))
                    .thenThrow(new ServiceNotFoundException("unknown-service"));

            mockMvc.perform(get("/services/unknown-service/dependencies"))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------------------
    // DELETE /services/{name}/dependencies/{depName}
    // ---------------------------------------------------------------------------

    @Nested
    class RemoveDependency {

        @Test
        void shouldReturn204WhenDependencyRemovedSuccessfully() throws Exception {
            doNothing().when(serviceManagementService)
                    .removeDependency("payment-service", "user-auth-service");

            mockMvc.perform(delete("/services/payment-service/dependencies/user-auth-service"))
                    .andExpect(status().isNoContent());
        }

        @Test
        void shouldReturn404WhenSourceServiceDoesNotExist() throws Exception {
            doThrow(new ServiceNotFoundException("unknown-service"))
                    .when(serviceManagementService).removeDependency("unknown-service", "user-auth-service");

            mockMvc.perform(delete("/services/unknown-service/dependencies/user-auth-service"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn404WhenDependencyEdgeDoesNotExist() throws Exception {
            doThrow(new DependencyNotFoundException("payment-service", "nonexistent-service"))
                    .when(serviceManagementService).removeDependency("payment-service", "nonexistent-service");

            mockMvc.perform(delete("/services/payment-service/dependencies/nonexistent-service"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(
                            allOf(containsString("payment-service"), containsString("nonexistent-service"))));
        }
    }

    // ---------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------

    private ServiceNode serviceNode(String name) {
        return new ServiceNode(name, null);
    }
}
