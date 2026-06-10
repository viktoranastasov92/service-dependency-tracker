package com.example.service_dependency_tracker.repository;

import com.example.service_dependency_tracker.domain.ServiceNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.Optional;

public interface ServiceRepository extends Neo4jRepository<ServiceNode, Long> {

    Optional<ServiceNode> findByName(String name);

    boolean existsByName(String name);
}
