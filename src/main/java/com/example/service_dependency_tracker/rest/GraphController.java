package com.example.service_dependency_tracker.rest;

import com.example.service_dependency_tracker.rest.dto.DependencyType;
import com.example.service_dependency_tracker.rest.dto.EdgeDTO;
import com.example.service_dependency_tracker.rest.dto.ServiceWithDepthDTO;
import com.example.service_dependency_tracker.rest.dto.TraversalResultDTO;
import com.example.service_dependency_tracker.rest.generated.GraphApi;
import com.example.service_dependency_tracker.service.GraphTraversalService;
import com.example.service_dependency_tracker.service.TraversalResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;

@RestController
public class GraphController implements GraphApi {

    private final GraphTraversalService graphTraversalService;

    public GraphController(GraphTraversalService graphTraversalService) {
        this.graphTraversalService = graphTraversalService;
    }

    @Override
    public ResponseEntity<TraversalResultDTO> getDownstream(String name) {
        return ResponseEntity.ok(toTraversalResultDTO(graphTraversalService.getDownstream(name)));
    }

    @Override
    public ResponseEntity<TraversalResultDTO> getUpstream(String name) {
        return ResponseEntity.ok(toTraversalResultDTO(graphTraversalService.getUpstream(name)));
    }

    private TraversalResultDTO toTraversalResultDTO(TraversalResult result) {
        List<ServiceWithDepthDTO> services = result.services().stream()
                .map(s -> new ServiceWithDepthDTO(s.name(), s.depth())
                        .description(s.description())
                        .createdAt(s.createdAt() != null ? s.createdAt().atOffset(ZoneOffset.UTC) : null))
                .toList();

        List<EdgeDTO> edges = result.edges().stream()
                .map(e -> new EdgeDTO(
                        e.fromService(),
                        e.toService(),
                        e.dependencyType() != null
                                ? DependencyType.fromValue(e.dependencyType())
                                : DependencyType.RUNTIME))
                .toList();

        return new TraversalResultDTO(result.origin(), services, edges, result.cycles());
    }
}
