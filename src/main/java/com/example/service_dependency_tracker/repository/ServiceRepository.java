package com.example.service_dependency_tracker.repository;

import com.example.service_dependency_tracker.domain.ServiceNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends Neo4jRepository<ServiceNode, Long> {

    Optional<ServiceNode> findByName(String name);

    boolean existsByName(String name);

    // Returns all services reachable downstream from the given service (i.e. what it depends on,
    // transitively), with each service's minimum hop count. DISTINCT guards against duplicates
    // on diamond graphs. Depth bound ensures termination on cyclic graphs (ADR-005, ADR-006).
    @Query("""
            MATCH path = (origin:Service {name: $name})-[:DEPENDS_ON*1..$maxDepth]->(dep:Service)
            WITH DISTINCT dep, min(length(path)) AS depth
            RETURN dep.name AS name, dep.description AS description, depth, dep.createdAt AS createdAt
            ORDER BY depth, dep.name
            """)
    List<ServiceWithDepthDTO> findAllDownstream(@Param("name") String name,
                                                @Param("maxDepth") int maxDepth);

    // Returns all services that transitively depend on the given service (upstream / blast radius),
    // with each service's minimum hop count. DISTINCT guards against duplicates.
    @Query("""
            MATCH path = (caller:Service)-[:DEPENDS_ON*1..$maxDepth]->(origin:Service {name: $name})
            WITH DISTINCT caller, min(length(path)) AS depth
            RETURN caller.name AS name, caller.description AS description, depth, caller.createdAt AS createdAt
            ORDER BY depth, caller.name
            """)
    List<ServiceWithDepthDTO> findAllUpstream(@Param("name") String name,
                                               @Param("maxDepth") int maxDepth);

    // Returns all dependency edges in the downstream subgraph reachable from the given service.
    // Used by the UI graph visualization component (ADR-005 Option 3).
    @Query("""
            MATCH (origin:Service {name: $name})-[:DEPENDS_ON*1..$maxDepth]->(dep:Service)
            MATCH (from:Service)-[rel:DEPENDS_ON]->(to:Service)
            WHERE (origin)-[:DEPENDS_ON*0..$maxDepth]->(from)
              AND (origin)-[:DEPENDS_ON*0..$maxDepth]->(to)
            RETURN DISTINCT from.name AS fromService, to.name AS toService,
                   rel.dependencyType AS dependencyType
            """)
    List<EdgeQueryResult> findSubgraphEdges(@Param("name") String name,
                                            @Param("maxDepth") int maxDepth);

    // Returns all dependency edges in the upstream subgraph (callers of the given service).
    @Query("""
            MATCH (caller:Service)-[:DEPENDS_ON*1..$maxDepth]->(origin:Service {name: $name})
            MATCH (from:Service)-[rel:DEPENDS_ON]->(to:Service)
            WHERE (from)-[:DEPENDS_ON*0..$maxDepth]->(origin)
              AND (to)-[:DEPENDS_ON*0..$maxDepth]->(origin)
            RETURN DISTINCT from.name AS fromService, to.name AS toService,
                   rel.dependencyType AS dependencyType
            """)
    List<EdgeQueryResult> findUpstreamSubgraphEdges(@Param("name") String name,
                                                    @Param("maxDepth") int maxDepth);

    // Returns all cycle paths reachable from the given service. Each inner list is an ordered
    // sequence of service names where the last element equals the first (closed loop).
    // Returns empty when no cycles exist in the reachable subgraph (ADR-006 Option 2).
    @Query("""
            MATCH path = (origin:Service {name: $name})-[:DEPENDS_ON*]->(origin)
            RETURN [node IN nodes(path) | node.name] AS cyclePath
            """)
    List<List<String>> findCyclesFrom(@Param("name") String name);
}
