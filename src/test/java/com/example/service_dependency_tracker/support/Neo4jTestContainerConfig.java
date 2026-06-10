package com.example.service_dependency_tracker.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;

/**
 * Singleton Neo4j container shared across all Testcontainers-backed test classes.
 * The static block starts the container once per JVM; @DynamicPropertySource wires
 * its bolt URL into the Spring context of every subclass.
 */
public abstract class Neo4jTestContainerConfig {

    protected static final Neo4jContainer<?> NEO4J =
            new Neo4jContainer<>("neo4j:5").withoutAuthentication();

    static {
        NEO4J.start();
    }

    @DynamicPropertySource
    static void neo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", NEO4J::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "");
    }
}
