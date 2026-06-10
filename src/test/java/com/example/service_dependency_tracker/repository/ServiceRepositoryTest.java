package com.example.service_dependency_tracker.repository;

import com.example.service_dependency_tracker.domain.DependsOnRelationship;
import com.example.service_dependency_tracker.domain.ServiceNode;
import com.example.service_dependency_tracker.support.Neo4jTestContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ServiceRepositoryTest extends Neo4jTestContainerConfig {

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private Neo4jTemplate neo4jTemplate;

    @BeforeEach
    void clearGraph() {
        neo4jTemplate.deleteAll(ServiceNode.class);
    }

    // ---------------------------------------------------------------------------
    // findByName
    // ---------------------------------------------------------------------------

    @Nested
    class FindByName {

        @Test
        void shouldReturnServiceWhenNameExists() {
            serviceRepository.save(new ServiceNode("payment-service", "Handles payments"));

            var result = serviceRepository.findByName("payment-service");

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("payment-service");
        }

        @Test
        void shouldReturnEmptyWhenNameDoesNotExist() {
            var result = serviceRepository.findByName("unknown-service");

            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------
    // findAllDownstream — DISTINCT and depth correctness
    // ---------------------------------------------------------------------------

    @Nested
    class FindAllDownstream {

        @Test
        void shouldReturnEmptyWhenServiceHasNoDependencies() {
            serviceRepository.save(new ServiceNode("payment-service", null));

            List<ServiceWithDepthDTO> result = serviceRepository.findAllDownstream("payment-service", 50);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnDirectDependencyAtDepthOne() {
            ServiceNode auth = serviceRepository.save(new ServiceNode("user-auth-service", null));
            ServiceNode payment = new ServiceNode("payment-service", null);
            payment.getDependsOn().add(new DependsOnRelationship(auth, "RUNTIME"));
            serviceRepository.save(payment);

            List<ServiceWithDepthDTO> result = serviceRepository.findAllDownstream("payment-service", 50);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("user-auth-service");
            assertThat(result.get(0).depth()).isEqualTo(1);
        }

        @Test
        void shouldReturnTransitiveDependenciesWithCorrectDepths() {
            ServiceNode db = serviceRepository.save(new ServiceNode("postgres-primary", null));
            ServiceNode auth = new ServiceNode("user-auth-service", null);
            auth.getDependsOn().add(new DependsOnRelationship(db, "RUNTIME"));
            auth = serviceRepository.save(auth);
            ServiceNode payment = new ServiceNode("payment-service", null);
            payment.getDependsOn().add(new DependsOnRelationship(auth, "RUNTIME"));
            serviceRepository.save(payment);

            List<ServiceWithDepthDTO> result = serviceRepository.findAllDownstream("payment-service", 50);

            assertThat(result).hasSize(2);
            assertThat(result).anySatisfy(dto -> {
                assertThat(dto.name()).isEqualTo("user-auth-service");
                assertThat(dto.depth()).isEqualTo(1);
            });
            assertThat(result).anySatisfy(dto -> {
                assertThat(dto.name()).isEqualTo("postgres-primary");
                assertThat(dto.depth()).isEqualTo(2);
            });
        }

        @Test
        void shouldReturnDistinctResultsOnDiamondGraph() {
            // A → B, A → C, B → D, C → D — D must appear exactly once
            ServiceNode d = serviceRepository.save(new ServiceNode("service-d", null));
            ServiceNode b = new ServiceNode("service-b", null);
            b.getDependsOn().add(new DependsOnRelationship(d, "RUNTIME"));
            b = serviceRepository.save(b);
            ServiceNode c = new ServiceNode("service-c", null);
            c.getDependsOn().add(new DependsOnRelationship(d, "RUNTIME"));
            c = serviceRepository.save(c);
            ServiceNode a = new ServiceNode("service-a", null);
            a.getDependsOn().add(new DependsOnRelationship(b, "RUNTIME"));
            a.getDependsOn().add(new DependsOnRelationship(c, "RUNTIME"));
            serviceRepository.save(a);

            List<ServiceWithDepthDTO> result = serviceRepository.findAllDownstream("service-a", 50);

            assertThat(result).hasSize(3);
            long countOfD = result.stream().filter(dto -> "service-d".equals(dto.name())).count();
            assertThat(countOfD).isEqualTo(1);
        }

        @Test
        void shouldTerminateOnCyclicGraphWithoutLooping() {
            ServiceNode a = serviceRepository.save(new ServiceNode("service-a", null));
            ServiceNode b = new ServiceNode("service-b", null);
            b.getDependsOn().add(new DependsOnRelationship(a, "RUNTIME"));
            b = serviceRepository.save(b);
            ServiceNode aFetched = serviceRepository.findByName("service-a").orElseThrow();
            aFetched.getDependsOn().add(new DependsOnRelationship(b, "RUNTIME"));
            serviceRepository.save(aFetched);

            List<ServiceWithDepthDTO> result = serviceRepository.findAllDownstream("service-a", 50);

            assertThat(result).isNotNull();
        }

        @Test
        void shouldRespectMaxDepthBound() {
            // Chain A→B→C→D — maxDepth=1 must return only B
            ServiceNode d = serviceRepository.save(new ServiceNode("service-d", null));
            ServiceNode c = new ServiceNode("service-c", null);
            c.getDependsOn().add(new DependsOnRelationship(d, "RUNTIME"));
            c = serviceRepository.save(c);
            ServiceNode b = new ServiceNode("service-b", null);
            b.getDependsOn().add(new DependsOnRelationship(c, "RUNTIME"));
            b = serviceRepository.save(b);
            ServiceNode a = new ServiceNode("service-a", null);
            a.getDependsOn().add(new DependsOnRelationship(b, "RUNTIME"));
            serviceRepository.save(a);

            List<ServiceWithDepthDTO> result = serviceRepository.findAllDownstream("service-a", 1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("service-b");
        }
    }

    // ---------------------------------------------------------------------------
    // findAllUpstream
    // ---------------------------------------------------------------------------

    @Nested
    class FindAllUpstream {

        @Test
        void shouldReturnServicesCallingIntoOriginAtCorrectDepths() {
            ServiceNode db = serviceRepository.save(new ServiceNode("postgres-primary", null));
            ServiceNode auth = new ServiceNode("user-auth-service", null);
            auth.getDependsOn().add(new DependsOnRelationship(db, "RUNTIME"));
            auth = serviceRepository.save(auth);
            ServiceNode payment = new ServiceNode("payment-service", null);
            payment.getDependsOn().add(new DependsOnRelationship(auth, "RUNTIME"));
            serviceRepository.save(payment);

            List<ServiceWithDepthDTO> result = serviceRepository.findAllUpstream("postgres-primary", 50);

            assertThat(result).hasSize(2);
            assertThat(result).anySatisfy(dto -> {
                assertThat(dto.name()).isEqualTo("user-auth-service");
                assertThat(dto.depth()).isEqualTo(1);
            });
            assertThat(result).anySatisfy(dto -> {
                assertThat(dto.name()).isEqualTo("payment-service");
                assertThat(dto.depth()).isEqualTo(2);
            });
        }

        @Test
        void shouldReturnEmptyWhenNothingDependsOnService() {
            serviceRepository.save(new ServiceNode("isolated-service", null));

            List<ServiceWithDepthDTO> result = serviceRepository.findAllUpstream("isolated-service", 50);

            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------
    // findCyclesFrom
    // ---------------------------------------------------------------------------

    @Nested
    class FindCyclesFrom {

        @Test
        void shouldReturnEmptyWhenNoReachableCyclesExist() {
            ServiceNode auth = serviceRepository.save(new ServiceNode("user-auth-service", null));
            ServiceNode payment = new ServiceNode("payment-service", null);
            payment.getDependsOn().add(new DependsOnRelationship(auth, "RUNTIME"));
            serviceRepository.save(payment);

            List<String> cycles = serviceRepository.findCyclesFrom("payment-service");

            assertThat(cycles).isEmpty();
        }

        @Test
        void shouldReturnDirectCycleWhenADependsOnBAndBDependsOnA() {
            ServiceNode a = serviceRepository.save(new ServiceNode("service-a", null));
            ServiceNode b = new ServiceNode("service-b", null);
            b.getDependsOn().add(new DependsOnRelationship(a, "RUNTIME"));
            b = serviceRepository.save(b);
            ServiceNode aFetched = serviceRepository.findByName("service-a").orElseThrow();
            aFetched.getDependsOn().add(new DependsOnRelationship(b, "RUNTIME"));
            serviceRepository.save(aFetched);

            List<String> cycles = serviceRepository.findCyclesFrom("service-a");

            assertThat(cycles).isNotEmpty();
            assertThat(cycles).anySatisfy(cyclePath -> {
                List<String> names = java.util.Arrays.asList(cyclePath.split(","));
                assertThat(names).contains("service-a");
                assertThat(names.get(0)).isEqualTo(names.get(names.size() - 1));
            });
        }

        @Test
        void shouldReturnSelfLoopWhenServiceDependsOnItself() {
            ServiceNode a = serviceRepository.save(new ServiceNode("service-a", null));
            ServiceNode aFetched = serviceRepository.findByName("service-a").orElseThrow();
            aFetched.getDependsOn().add(new DependsOnRelationship(a, "RUNTIME"));
            serviceRepository.save(aFetched);

            List<String> cycles = serviceRepository.findCyclesFrom("service-a");

            assertThat(cycles).isNotEmpty();
        }

        @Test
        void shouldReturnTransitiveCycleForThreeNodeRing() {
            // Build A→B→C→A bottom-up so the in-memory chain is complete before the final save.
            // Pattern mirrors the working 2-node cycle test: cascade during the final save
            // terminates on the original `a` node because SDN recognises the same Neo4j ID.
            ServiceNode a = serviceRepository.save(new ServiceNode("service-a", null));

            ServiceNode c = new ServiceNode("service-c", null);
            c.getDependsOn().add(new DependsOnRelationship(a, "RUNTIME")); // C→A
            c = serviceRepository.save(c);

            ServiceNode b = new ServiceNode("service-b", null);
            b.getDependsOn().add(new DependsOnRelationship(c, "RUNTIME")); // B→C (c carries C→A)
            b = serviceRepository.save(b);

            ServiceNode aFetched = serviceRepository.findByName("service-a").orElseThrow();
            aFetched.getDependsOn().add(new DependsOnRelationship(b, "RUNTIME")); // A→B
            serviceRepository.save(aFetched);

            List<String> cycles = serviceRepository.findCyclesFrom("service-a");

            assertThat(cycles).isNotEmpty();
            assertThat(cycles).anySatisfy(cyclePath -> {
                List<String> names = java.util.Arrays.asList(cyclePath.split(","));
                assertThat(names).contains("service-a", "service-b", "service-c");
            });
        }
    }
}
