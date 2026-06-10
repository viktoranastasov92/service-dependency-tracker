package com.example.service_dependency_tracker.domain;

import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.Instant;

@RelationshipProperties
public class DependsOnRelationship {

    @RelationshipId
    private Long id;

    @TargetNode
    private ServiceNode target;

    private String dependencyType;
    private Instant createdAt;

    public DependsOnRelationship() {}

    public DependsOnRelationship(ServiceNode target, String dependencyType) {
        this.target = target;
        this.dependencyType = dependencyType;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ServiceNode getTarget() { return target; }
    public void setTarget(ServiceNode target) { this.target = target; }

    public String getDependencyType() { return dependencyType; }
    public void setDependencyType(String dependencyType) { this.dependencyType = dependencyType; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
