package com.example.service_dependency_tracker.repository;

import java.time.Instant;

public class ServiceWithDepthDTO {

    private String name;
    private String description;
    private int depth;
    private Instant createdAt;

    public ServiceWithDepthDTO() {}

    public ServiceWithDepthDTO(String name, String description, int depth, Instant createdAt) {
        this.name = name;
        this.description = description;
        this.depth = depth;
        this.createdAt = createdAt;
    }

    public String name() { return name; }
    public String description() { return description; }
    public int depth() { return depth; }
    public Instant createdAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setDepth(int depth) { this.depth = depth; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
