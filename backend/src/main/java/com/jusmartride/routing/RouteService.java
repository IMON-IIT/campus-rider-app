package com.jusmartride.routing;

import com.jusmartride.algorithms.DijkstraShortestPath;
import com.jusmartride.algorithms.RouteResult;
import com.jusmartride.campus.CampusService;
import com.jusmartride.matching.*;
import com.jusmartride.ride.DriverTrip;
import com.jusmartride.ride.DriverTripRepository;
import com.jusmartride.ride.TripStop;
import com.jusmartride.vehicle.Vehicle;
import com.jusmartride.vehicle.VehicleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RouteService {

    private final CampusService campusService;
    private final VehicleRepository vehicleRepository;
    private final DriverTripRepository driverTripRepository;
    private final DetourCalculator detourCalculator;
    private final CandidateScorer candidateScorer;

    public RouteService(CampusService campusService,
                        VehicleRepository vehicleRepository,
                        DriverTripRepository driverTripRepository,
                        @Value("${jusmartride.routing.max-detour-ratio:0.45}") double maxDetourRatio,
                        @Value("${jusmartride.routing.max-extra-distance-meters:750.0}") double maxExtraDist,
                        @Value("${jusmartride.routing.max-extra-time-seconds:240}") long maxExtraTime) {
        this.campusService = campusService;
        this.vehicleRepository = vehicleRepository;
        this.driverTripRepository = driverTripRepository;
        this.detourCalculator = new DetourCalculator(maxDetourRatio, maxExtraDist, maxExtraTime);
        this.candidateScorer = new CandidateScorer();
    }

    public RouteResult findShortestPath(String sourceId, String targetId) {
        DijkstraShortestPath dijkstra = new DijkstraShortestPath(campusService.getCampusGraph());
        return dijkstra.findShortestPath(sourceId, targetId);
    }

    @Transactional(readOnly = true)
    public List<CandidateRide> findRideOptions(String pickupId, String destinationId, int passengerCount) {
        DijkstraShortestPath dijkstra = new DijkstraShortestPath(campusService.getCampusGraph());
        RideMatchingService matchingService = new RideMatchingService(
                campusService.getCampusGraph(),
                dijkstra,
                detourCalculator,
                candidateScorer
        );

        List<DriverTripSnapshot> snapshots = new ArrayList<>();
        long now = System.currentTimeMillis();

        // 1. Gather all online vehicles
        List<Vehicle> onlineVehicles = vehicleRepository.findByIsOnlineTrue();
        for (Vehicle v : onlineVehicles) {
            // Find active trip if any
            List<DriverTrip> trips = driverTripRepository.findByVehicleAndStatusIn(v, List.of("AVAILABLE", "ARRIVING", "IN_PROGRESS"))
                    .map(List::of).orElse(List.of());

            long lastLocMs = v.getLastLocationUpdate() != null ? v.getLastLocationUpdate().toEpochMilli() : 0L;
            String currentLocId = v.getCurrentLocation() != null ? v.getCurrentLocation().getId() : "dairy_gate";

            if (trips.isEmpty()) {
                // Idle vehicle
                snapshots.add(DriverTripSnapshot.idle(
                        v.getId(),
                        v.getDriver().getId(),
                        v.getDriver().getFullName(),
                        v.getRickshawNumber(),
                        v.getCapacity(),
                        currentLocId,
                        lastLocMs
                ));
            } else {
                DriverTrip trip = trips.get(0);
                List<TripStopSnapshot> stopSnapshots = new ArrayList<>();
                for (TripStop stop : trip.getScheduledStops()) {
                    stopSnapshots.add(new TripStopSnapshot(
                            stop.getLocation().getId(),
                            stop.getStopOrder(),
                            stop.getBoardingCount(),
                            stop.getAlightingCount(),
                            stop.getPassengerRequestId()
                    ));
                }

                snapshots.add(new DriverTripSnapshot(
                        trip.getId(),
                        v.getId(),
                        v.getDriver().getId(),
                        v.getDriver().getFullName(),
                        v.getRickshawNumber(),
                        v.getCapacity(),
                        trip.getStatus(),
                        currentLocId,
                        lastLocMs,
                        stopSnapshots,
                        trip.getOccupiedSeats()
                ));
            }
        }

        return matchingService.matchRides(pickupId, destinationId, passengerCount, snapshots, now);
    }
}
