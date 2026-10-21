package com.jusmartride.matching;

import com.jusmartride.algorithms.DijkstraShortestPath;
import com.jusmartride.graph.CampusGraph;
import com.jusmartride.graph.Edge;
import com.jusmartride.graph.Vertex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Ride Matching Service & Route Insertion Tests")
class RideMatchingServiceTest {

    private CampusGraph graph;
    private DijkstraShortestPath dijkstra;
    private RideMatchingService matchingService;

    @BeforeEach
    void setUp() {
        graph = new CampusGraph();

        // Campus corridor: Dairy Gate -> Shaheed Minar -> Central Library -> Chourangi -> Bottola
        // Distances:
        // Dairy Gate -> Shaheed Minar (120m)
        // Shaheed Minar -> Central Library (180m)
        // Central Library -> Chourangi (200m)
        // Chourangi -> Bottola (400m)
        // Total direct corridor Dairy Gate to Bottola = 900m
        Vertex dairy = new Vertex("dairy_gate", "Dairy Gate", "ডেইরি গেইট", 23.8798, 90.2693, "GATE", null, true);
        Vertex minar = new Vertex("shaheed_minar", "Shaheed Minar", "শহীদ মিনার", 23.8797, 90.2682, "LANDMARK", null, true);
        Vertex library = new Vertex("central_library", "Central Library", "কেন্দ্রীয় গ্রন্থাগার", 23.8797, 90.2666, "ACADEMIC", null, true);
        Vertex chourangi = new Vertex("chourangi_mor", "Chourangi Mor", "চৌরঙ্গী মোড়", 23.8815, 90.2672, "JUNCTION", null, true);
        Vertex bottola = new Vertex("bottola", "Bottola", "বটতলা", 23.8844, 90.2588, "FOOD", null, true);

        // North gate far away for excessive detour test:
        Vertex bishmile = new Vertex("bishmile_gate", "Bishmile Gate", "বিশমাইল", 23.8947, 90.2719, "GATE", null, true);

        graph.addVertex(dairy);
        graph.addVertex(minar);
        graph.addVertex(library);
        graph.addVertex(chourangi);
        graph.addVertex(bottola);
        graph.addVertex(bishmile);

        graph.addEdge(new Edge("e1", "dairy_gate", "shaheed_minar", "R1", 120.0, 36L, true, true, "Paved", "VERIFIED"));
        graph.addEdge(new Edge("e2", "shaheed_minar", "central_library", "R2", 180.0, 54L, true, true, "Paved", "VERIFIED"));
        graph.addEdge(new Edge("e3", "central_library", "chourangi_mor", "R3", 200.0, 60L, true, true, "Paved", "VERIFIED"));
        graph.addEdge(new Edge("e4", "chourangi_mor", "bottola", "R4", 400.0, 120L, true, true, "Paved", "VERIFIED"));
        graph.addEdge(new Edge("e5", "chourangi_mor", "bishmile_gate", "R5", 1500.0, 450L, true, true, "Paved", "VERIFIED"));

        dijkstra = new DijkstraShortestPath(graph);
        DetourCalculator detourCalc = new DetourCalculator(0.40, 600.0, 240L); // 40% max detour, 600m max extra
        CandidateScorer scorer = new CandidateScorer();
        matchingService = new RideMatchingService(graph, dijkstra, detourCalc, scorer);
    }

    @Test
    @DisplayName("Compatible shared ride: Passenger B joins existing ride from Shaheed Minar to Bottola")
    void testMatchRides_CompatibleSharedRide() {
        long now = System.currentTimeMillis();

        // Existing trip: Passenger A is already on board, heading to Bottola.
        // Current driver position: Dairy Gate.
        // Scheduled stop: Bottola (alighting: 1).
        // Total capacity: 3, currently occupied: 1. Available: 2.
        TripStopSnapshot stopBottola = new TripStopSnapshot("bottola", 1, 0, 1, "req_A");
        DriverTripSnapshot activeTrip = new DriverTripSnapshot(
                "trip_001", "veh_001", "driver_001", "Rahim Mia", "JU-RICK-101",
                3, "IN_PROGRESS", "dairy_gate", now - 10000,
                List.of(stopBottola), 1
        );

        // Passenger B requests ride from Shaheed Minar to Bottola for 1 seat
        List<CandidateRide> candidates = matchingService.matchRides(
                "shaheed_minar", "bottola", 1,
                List.of(activeTrip), now
        );

        assertFalse(candidates.isEmpty(), "Should find a compatible candidate ride");
        CandidateRide bestRide = candidates.get(0);

        assertTrue(bestRide.isSharedRide(), "Should be identified as a shared carpool ride");
        assertTrue(bestRide.isRecommended(), "Best candidate should be marked recommended");
        assertNotNull(bestRide.getRecommendationReason());
        assertEquals("driver_001", bestRide.getDriverId());
        assertEquals(2, bestRide.getAvailableSeats());
        assertEquals(0.0, bestRide.getDetourRatio(), 0.01, "Shaheed Minar is directly on the route, 0 extra detour");
    }

    @Test
    @DisplayName("Capacity constraint: Vehicle is full (3/3), request for new seat is rejected")
    void testMatchRides_VehicleFullCapacityExceeded() {
        long now = System.currentTimeMillis();

        // Existing trip has 3 occupied seats (full capacity)
        TripStopSnapshot stopBottola = new TripStopSnapshot("bottola", 1, 0, 3, "req_full");
        DriverTripSnapshot fullTrip = new DriverTripSnapshot(
                "trip_002", "veh_002", "driver_002", "Karim Ali", "JU-RICK-102",
                3, "IN_PROGRESS", "dairy_gate", now - 5000,
                List.of(stopBottola), 3 // 3 occupied out of 3
        );

        List<CandidateRide> candidates = matchingService.matchRides(
                "shaheed_minar", "bottola", 1,
                List.of(fullTrip), now
        );

        assertTrue(candidates.isEmpty(), "Full vehicle cannot accommodate new passenger");
    }

    @Test
    @DisplayName("Detour constraint: Unreasonable route detour is rejected")
    void testMatchRides_ExcessiveDetourRejected() {
        long now = System.currentTimeMillis();

        // Existing trip is going to Bottola
        TripStopSnapshot stopBottola = new TripStopSnapshot("bottola", 1, 0, 1, "req_A");
        DriverTripSnapshot activeTrip = new DriverTripSnapshot(
                "trip_003", "veh_003", "driver_003", "Jamal Uddin", "JU-RICK-103",
                3, "IN_PROGRESS", "dairy_gate", now - 5000,
                List.of(stopBottola), 1
        );

        // Passenger B wants to go to Bishmile Gate (1500m detour from Chourangi)
        List<CandidateRide> candidates = matchingService.matchRides(
                "shaheed_minar", "bishmile_gate", 1,
                List.of(activeTrip), now
        );

        assertTrue(candidates.isEmpty(), "Excessive detour should be rejected by constraint checker");
    }

    @Test
    @DisplayName("Idle driver matches direct ride")
    void testMatchRides_IdleDriverDirectRide() {
        long now = System.currentTimeMillis();

        // Idle driver waiting at Dairy Gate
        DriverTripSnapshot idleDriver = DriverTripSnapshot.idle(
                "veh_004", "driver_004", "Sultan Khan", "JU-RICK-104",
                3, "dairy_gate", now - 3000
        );

        List<CandidateRide> candidates = matchingService.matchRides(
                "dairy_gate", "bottola", 2,
                List.of(idleDriver), now
        );

        assertFalse(candidates.isEmpty());
        CandidateRide candidate = candidates.get(0);
        assertFalse(candidate.isSharedRide());
        assertTrue(candidate.isRecommended());
        assertEquals("driver_004", candidate.getDriverId());
        assertEquals(3, candidate.getAvailableSeats());
    }

    @Test
    @DisplayName("Stale location report excludes driver")
    void testMatchRides_StaleLocationExcluded() {
        long now = System.currentTimeMillis();
        long staleTime = now - (15 * 60 * 1000L); // 15 minutes ago

        DriverTripSnapshot staleDriver = DriverTripSnapshot.idle(
                "veh_005", "driver_005", "Old Position", "JU-RICK-105",
                3, "dairy_gate", staleTime
        );

        List<CandidateRide> candidates = matchingService.matchRides(
                "dairy_gate", "bottola", 1,
                List.of(staleDriver), now
        );

        assertTrue(candidates.isEmpty(), "Driver with stale GPS report > 5 min must be excluded");
    }
}
