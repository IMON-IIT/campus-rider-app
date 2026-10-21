package com.jusmartride.campus;

import com.jusmartride.common.ApiResponse;
import com.jusmartride.graph.Vertex;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/campus")
public class CampusController {

    private final CampusService campusService;

    public CampusController(CampusService campusService) {
        this.campusService = campusService;
    }

    @GetMapping("/locations")
    public ResponseEntity<ApiResponse<Collection<Vertex>>> getAllLocations() {
        return ResponseEntity.ok(ApiResponse.ok(campusService.getAllVertices()));
    }

    @GetMapping("/locations/nearest")
    public ResponseEntity<ApiResponse<Vertex>> getNearestLocation(
            @RequestParam double lat,
            @RequestParam double lng) {
        return ResponseEntity.ok(ApiResponse.ok(campusService.findNearestVertex(lat, lng)));
    }

    @GetMapping("/locations/search")
    public ResponseEntity<ApiResponse<List<Vertex>>> searchLocations(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        List<Vertex> matches = campusService.search(q, limit);
        return ResponseEntity.ok(ApiResponse.ok(matches));
    }

    @GetMapping("/graph")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGraph() {
        return ResponseEntity.ok(ApiResponse.ok(campusService.getGraphMetadata()));
    }
}
