package com.example.service_dependency_tracker.repository;

public class EdgeQueryResult {

    private String fromService;
    private String toService;
    private String dependencyType;

    public EdgeQueryResult() {}

    public EdgeQueryResult(String fromService, String toService, String dependencyType) {
        this.fromService = fromService;
        this.toService = toService;
        this.dependencyType = dependencyType;
    }

    public String fromService() { return fromService; }
    public String toService() { return toService; }
    public String dependencyType() { return dependencyType; }

    public void setFromService(String fromService) { this.fromService = fromService; }
    public void setToService(String toService) { this.toService = toService; }
    public void setDependencyType(String dependencyType) { this.dependencyType = dependencyType; }
}
