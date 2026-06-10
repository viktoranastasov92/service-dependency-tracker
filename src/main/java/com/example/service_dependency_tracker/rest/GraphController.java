package com.example.service_dependency_tracker.rest;

import com.example.service_dependency_tracker.rest.generated.GraphApi;
import com.example.service_dependency_tracker.service.GraphTraversalService;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GraphController implements GraphApi {

    private final GraphTraversalService graphTraversalService;

    public GraphController(GraphTraversalService graphTraversalService) {
        this.graphTraversalService = graphTraversalService;
    }
}
