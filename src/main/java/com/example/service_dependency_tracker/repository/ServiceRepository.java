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
    // Neo4j 5 does not allow parameters in path length bounds — the hard cap *1..50 is the
    // termination guarantee; $maxDepth applies additional filtering via WHERE.
    @Query("""
            MATCH path = (origin:Service {name: $name})-[:DEPENDS_ON*1..50]->(dep:Service)
            WHERE length(path) <= $maxDepth
            WITH DISTINCT dep, min(length(path)) AS depth
            RETURN dep.name AS name, dep.description AS description, depth, dep.createdAt AS createdAt
            ORDER BY depth, dep.name
            """)
    List<ServiceWithDepthDTO> findAllDownstream(@Param("name") String name,
                                                @Param("maxDepth") int maxDepth);

    // Returns all services that transitively depend on the given service (upstream / blast radius),
    // with each service's minimum hop count. DISTINCT guards against duplicates.
    @Query("""
            MATCH path = (caller:Service)-[:DEPENDS_ON*1..50]->(origin:Service {name: $name})
            WHERE length(path) <= $maxDepth
            WITH DISTINCT caller, min(length(path)) AS depth
            RETURN caller.name AS name, caller.description AS description, depth, caller.createdAt AS createdAt
            ORDER BY depth, caller.name
            """)
    List<ServiceWithDepthDTO> findAllUpstream(@Param("name") String name,
                                               @Param("maxDepth") int maxDepth);

    // Returns all dependency edges in the downstream subgraph reachable from the given service.
    // Used by the UI graph visualization component (ADR-005 Option 3).
    @Query("""
            MATCH (origin:Service {name: $name})-[:DEPENDS_ON*1..50]->(dep:Service)
            MATCH (from:Service)-[rel:DEPENDS_ON]->(to:Service)
            WHERE (origin)-[:DEPENDS_ON*0..50]->(from)
              AND (origin)-[:DEPENDS_ON*0..50]->(to)
            RETURN DISTINCT from.name AS fromService, to.name AS toService,
                   rel.dependencyType AS dependencyType
            """)
    List<EdgeQueryResult> findSubgraphEdges(@Param("name") String name,
                                            @Param("maxDepth") int maxDepth);

    // Returns all dependency edges in the upstream subgraph (callers of the given service).
    @Query("""
            MATCH (caller:Service)-[:DEPENDS_ON*1..50]->(origin:Service {name: $name})
            MATCH (from:Service)-[rel:DEPENDS_ON]->(to:Service)
            WHERE (from)-[:DEPENDS_ON*0..50]->(origin)
              AND (to)-[:DEPENDS_ON*0..50]->(origin)
            RETURN DISTINCT from.name AS fromService, to.name AS toService,
                   rel.dependencyType AS dependencyType
            """)
    List<EdgeQueryResult> findUpstreamSubgraphEdges(@Param("name") String name,
                                                    @Param("maxDepth") int maxDepth);

    // Returns all cycle paths reachable from the given service. Each string is a comma-joined
    // sequence of service names where the last element equals the first (closed loop).
    // Comma-joining works because ADR-014 names match ^[a-z0-9-]+$ (no commas).
    // Returns empty when no cycles exist in the reachable subgraph (ADR-006 Option 2).
    // The Cypher list-to-Java-List mapping in SDN flattens list columns into individual rows,
    // so the path is serialised to a scalar string here and parsed in CycleReportingService.
    @Query("""
            MATCH path = (origin:Service {name: $name})-[:DEPENDS_ON*1..50]->(origin)
            RETURN reduce(acc = '', name IN [node IN nodes(path) | node.name] |
                          acc + CASE WHEN acc = '' THEN '' ELSE ',' END + name) AS cyclePath
            """)
    List<String> findCyclesFrom(@Param("name") String name);
}
