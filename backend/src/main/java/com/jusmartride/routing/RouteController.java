package com.jusmartride.routing;

import com.jusmartride.algorithms.RouteResult;
import com.jusmartride.common.ApiResponse;
import com.jusmartride.matching.CandidateRide;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/shortest-path")
    public ResponseEntity<ApiResponse<RouteResult>> getShortestPath(@Valid @RequestBody ShortestPathRequest request) {
        RouteResult result = routeService.findShortestPath(request.getSourceId(), request.getTargetId());
        return ResponseEntity.ok(ApiResponse.ok("Shortest path calculated via Java Dijkstra algorithm", result));
    }

    @PostMapping("/ride-options")
    public ResponseEntity<ApiResponse<List<CandidateRide>>> getRideOptions(@Valid @RequestBody RideOptionsRequest request) {
        List<CandidateRide> options = routeService.findRideOptions(
                request.getPickupId(),
                request.getDestinationId(),
                request.getPassengerCount()
        );
        return ResponseEntity.ok(ApiResponse.ok("Ride options evaluated and ranked", options));
    }
}
