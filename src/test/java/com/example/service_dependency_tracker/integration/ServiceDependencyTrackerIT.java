package com.example.service_dependency_tracker.integration;

import com.example.service_dependency_tracker.domain.ServiceNode;
import com.example.service_dependency_tracker.support.Neo4jTestContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ServiceDependencyTrackerIT extends Neo4jTestContainerConfig {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private Neo4jTemplate neo4jTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        neo4jTemplate.deleteAll(ServiceNode.class);
    }

    // ---------------------------------------------------------------------------
    // Service lifecycle
    // ---------------------------------------------------------------------------

    @Nested
    class ServiceLifecycle {

        @Test
        void shouldRegisterAndRetrieveAService() throws Exception {
            mockMvc.perform(post("/api/v1/services")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"payment-service","description":"Handles payments"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("payment-service"));

            mockMvc.perform(get("/api/v1/services/payment-service"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("payment-service"))
                    .andExpect(jsonPath("$.description").value("Handles payments"));
        }

        @Test
        void shouldReturn409WhenRegisteringDuplicateServiceName() throws Exception {
            mockMvc.perform(post("/api/v1/services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"payment-service"}
                            """));

            mockMvc.perform(post("/api/v1/services")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"payment-service"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        void shouldDeleteServiceAndReturn404OnSubsequentGet() throws Exception {
            mockMvc.perform(post("/api/v1/services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"payment-service"}
                            """));

            mockMvc.perform(delete("/api/v1/services/payment-service"))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/services/payment-service"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldListAllRegisteredServices() throws Exception {
            registerService("payment-service");
            registerService("user-auth-service");

            mockMvc.perform(get("/api/v1/services"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].name",
                            containsInAnyOrder("payment-service", "user-auth-service")));
        }
    }

    // ---------------------------------------------------------------------------
    // Dependency edges
    // ---------------------------------------------------------------------------

    @Nested
    class DependencyEdges {

        @Test
        void shouldAddDependencyAndReturnItInDirectDependenciesList() throws Exception {
            registerService("payment-service");
            registerService("user-auth-service");

            mockMvc.perform(post("/api/v1/services/payment-service/dependencies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"dependsOnName":"user-auth-service","dependencyType":"RUNTIME"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fromService").value("payment-service"))
                    .andExpect(jsonPath("$.toService").value("user-auth-service"))
                    .andExpect(jsonPath("$.dependencyType").value("RUNTIME"));

            mockMvc.perform(get("/api/v1/services/payment-service/dependencies"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].toService").value("user-auth-service"));
        }

        @Test
        void shouldStoreCyclicDependencyWithout409() throws Exception {
            registerService("service-a");
            registerService("service-b");
            addDependency("service-a", "service-b", "RUNTIME");

            // B→A creates a cycle — must succeed, not return 409 (ADR-006)
            mockMvc.perform(post("/api/v1/services/service-b/dependencies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"dependsOnName":"service-a","dependencyType":"RUNTIME"}
                                    """))
                    .andExpect(status().isCreated());
        }

        @Test
        void shouldRemoveDependencyEdge() throws Exception {
            registerService("payment-service");
            registerService("user-auth-service");
            addDependency("payment-service", "user-auth-service", "RUNTIME");

            mockMvc.perform(delete("/api/v1/services/payment-service/dependencies/user-auth-service"))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/services/payment-service/dependencies"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // ---------------------------------------------------------------------------
    // Graph traversal
    // ---------------------------------------------------------------------------

    @Nested
    class GraphTraversal {

        @Test
        void shouldReturnDownstreamChainWithCorrectDepths() throws Exception {
            registerService("payment-service");
            registerService("user-auth-service");
            registerService("postgres-primary");
            addDependency("payment-service", "user-auth-service", "RUNTIME");
            addDependency("user-auth-service", "postgres-primary", "RUNTIME");

            mockMvc.perform(get("/api/v1/services/payment-service/downstream"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.origin").value("payment-service"))
                    .andExpect(jsonPath("$.services", hasSize(2)))
                    .andExpect(jsonPath("$.services[?(@.name=='user-auth-service')].depth").value(1))
                    .andExpect(jsonPath("$.services[?(@.name=='postgres-primary')].depth").value(2))
                    .andExpect(jsonPath("$.cycles", empty()));
        }

        @Test
        void shouldReturnUpstreamChainRepresentingBlastRadius() throws Exception {
            registerService("payment-service");
            registerService("user-auth-service");
            registerService("postgres-primary");
            addDependency("payment-service", "user-auth-service", "RUNTIME");
            addDependency("user-auth-service", "postgres-primary", "RUNTIME");

            mockMvc.perform(get("/api/v1/services/postgres-primary/upstream"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.origin").value("postgres-primary"))
                    .andExpect(jsonPath("$.services", hasSize(2)));
        }

        @Test
        void shouldReturnDistinctResultsOnDiamondGraph() throws Exception {
            registerService("service-a");
            registerService("service-b");
            registerService("service-c");
            registerService("service-d");
            addDependency("service-a", "service-b", "RUNTIME");
            addDependency("service-a", "service-c", "RUNTIME");
            addDependency("service-b", "service-d", "RUNTIME");
            addDependency("service-c", "service-d", "RUNTIME");

            mockMvc.perform(get("/api/v1/services/service-a/downstream"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.services", hasSize(3)))
                    .andExpect(jsonPath("$.services[?(@.name=='service-d')]", hasSize(1)));
        }

        @Test
        void shouldAnnotateCyclesInDownstreamResponse() throws Exception {
            registerService("service-a");
            registerService("service-b");
            addDependency("service-a", "service-b", "RUNTIME");
            addDependency("service-b", "service-a", "RUNTIME");

            mockMvc.perform(get("/api/v1/services/service-a/downstream"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cycles").isArray())
                    .andExpect(jsonPath("$.cycles", not(empty())));
        }

        @Test
        void shouldReturnEmptyDownstreamForIsolatedService() throws Exception {
            registerService("isolated-service");

            mockMvc.perform(get("/api/v1/services/isolated-service/downstream"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.services", empty()))
                    .andExpect(jsonPath("$.cycles", empty()));
        }
    }

    // ---------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------

    private void registerService(String name) throws Exception {
        mockMvc.perform(post("/api/v1/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"));
    }

    private void addDependency(String from, String to, String type) throws Exception {
        mockMvc.perform(post("/api/v1/services/" + from + "/dependencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dependsOnName\":\"" + to + "\",\"dependencyType\":\"" + type + "\"}"));
    }
}
