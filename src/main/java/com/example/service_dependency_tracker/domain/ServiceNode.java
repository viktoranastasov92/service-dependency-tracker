package com.example.service_dependency_tracker.domain;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Node("Service")
public class ServiceNode {

    @Id @GeneratedValue
    private Long id;

    private String name;
    private String description;
    private Instant createdAt;

    @Relationship(type = "DEPENDS_ON", direction = Relationship.Direction.OUTGOING)
    private List<DependsOnRelationship> dependsOn = new ArrayList<>();

    public ServiceNode() {}

    public ServiceNode(String name, String description) {
        this.name = name;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<DependsOnRelationship> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<DependsOnRelationship> dependsOn) { this.dependsOn = dependsOn; }
}
