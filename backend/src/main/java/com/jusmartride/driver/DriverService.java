package com.jusmartride.driver;

import com.jusmartride.algorithms.DijkstraShortestPath;
import com.jusmartride.algorithms.RouteResult;
import com.jusmartride.campus.CampusLocation;
import com.jusmartride.campus.CampusLocationRepository;
import com.jusmartride.campus.CampusService;
import com.jusmartride.common.GeoUtils;
import com.jusmartride.graph.Vertex;
import com.jusmartride.matching.CandidateRide;
import com.jusmartride.matching.DetourCalculator;
import com.jusmartride.matching.DriverTripSnapshot;
import com.jusmartride.matching.RideMatchingService;
import com.jusmartride.matching.TripStopSnapshot;
import com.jusmartride.notification.NotificationService;
import com.jusmartride.ride.*;
import com.jusmartride.tracking.DriverLocationUpdate;
import com.jusmartride.user.User;
import com.jusmartride.user.UserRepository;
import com.jusmartride.vehicle.Vehicle;
import com.jusmartride.vehicle.VehicleRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DriverService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverTripRepository driverTripRepository;
    private final TripStopRepository tripStopRepository;
    private final RideRequestRepository rideRequestRepository;
    private final CampusLocationRepository campusLocationRepository;
    private final CampusService campusService;
    private final NotificationService notificationService;
    private final DetourCalculator detourCalculator = new DetourCalculator();

    public DriverService(UserRepository userRepository,
                         VehicleRepository vehicleRepository,
                         DriverTripRepository driverTripRepository,
                         TripStopRepository tripStopRepository,
                         RideRequestRepository rideRequestRepository,
                         CampusLocationRepository campusLocationRepository,
                         CampusService campusService,
                         NotificationService notificationService) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.driverTripRepository = driverTripRepository;
        this.tripStopRepository = tripStopRepository;
        this.rideRequestRepository = rideRequestRepository;
        this.campusLocationRepository = campusLocationRepository;
        this.campusService = campusService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Vehicle setAvailability(String driverId, boolean isOnline, String locationId) {
        Vehicle vehicle = vehicleRepository.findByDriverId(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver has no registered vehicle: " + driverId));

        vehicle.setOnline(isOnline);
        if (locationId != null && !locationId.isBlank()) {
            // Manual stand selection (no GPS): place the rickshaw at the stand's coordinates
            campusLocationRepository.findById(locationId).ifPresent(loc -> {
                vehicle.setCurrentLocation(loc);
                vehicle.setCurrentLatitude(loc.getLatitude());
                vehicle.setCurrentLongitude(loc.getLongitude());
            });
            vehicle.setLocationSource("MANUAL");
        }
        vehicle.setLastLocationUpdate(Instant.now());

        return vehicleRepository.save(vehicle);
    }

    private static final double RICKSHAW_SPEED_MPS = 2.8; // ~10 km/h on campus roads
    private static final long REQUEST_VISIBLE_SECONDS = 15 * 60;

    @Transactional(readOnly = true)
    public List<RideRequestResponseDto> getIncomingRequests(String driverId) {
        Vehicle vehicle = vehicleRepository.findByDriverId(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver has no registered vehicle: " + driverId));
        if (!vehicle.isOnline()) {
            return List.of();
        }

        Instant cutoff = Instant.now().minusSeconds(REQUEST_VISIBLE_SECONDS);
        DijkstraShortestPath dijkstra = new DijkstraShortestPath(campusService.getCampusGraph());
        String driverLocId = vehicle.getCurrentLocation() != null ? vehicle.getCurrentLocation().getId() : null;

        // Requests sent to this driver, plus broadcast requests this driver has not declined
        return rideRequestRepository.findByStatus(RideStatus.REQUESTED).stream()
                .filter(r -> r.getRequestedAt() == null || r.getRequestedAt().isAfter(cutoff))
                .filter(r -> r.getDriver() == null
                        ? !r.getRejectedByDriverIds().contains(driverId)
                        : r.getDriver().getId().equals(driverId))
                .map(r -> {
                    RideRequestResponseDto dto = RideRequestResponseDto.fromEntity(r);
                    if (driverLocId != null) {
                        RouteResult toPickup = dijkstra.findShortestPath(driverLocId, r.getPickupLocation().getId());
                        if (toPickup.isReachable()) {
                            dto.setDriverToPickupMeters(toPickup.getTotalDistanceMeters());
                        }
                    }
                    return dto;
                })
                .sorted(Comparator.comparing(
                        (RideRequestResponseDto d) -> d.getDriverToPickupMeters() != null ? d.getDriverToPickupMeters() : Double.MAX_VALUE))
                .collect(Collectors.toList());
    }

    /**
     * Online rickshaws within {@code radiusMeters} of a GPS point, closest first.
     */
    @Transactional(readOnly = true)
    public List<NearbyDriverDto> findNearbyDrivers(double latitude, double longitude, double radiusMeters) {
        long nowMs = System.currentTimeMillis();
        List<NearbyDriverDto> result = new ArrayList<>();

        for (Vehicle v : vehicleRepository.findByIsOnlineTrue()) {
            Double lat = v.getCurrentLatitude();
            Double lng = v.getCurrentLongitude();
            if ((lat == null || lng == null) && v.getCurrentLocation() != null) {
                lat = v.getCurrentLocation().getLatitude();
                lng = v.getCurrentLocation().getLongitude();
            }
            if (lat == null || lng == null) {
                continue;
            }

            double distance = GeoUtils.haversineMeters(latitude, longitude, lat, lng);
            if (distance > radiusMeters) {
                continue;
            }

            Optional<DriverTrip> trip = driverTripRepository.findByVehicleAndStatusIn(v, List.of("ARRIVING", "IN_PROGRESS"));
            int occupied = trip.map(DriverTrip::getOccupiedSeats).orElse(0);

            NearbyDriverDto dto = new NearbyDriverDto();
            dto.setDriverId(v.getDriver().getId());
            dto.setVehicleId(v.getId());
            dto.setDriverName(v.getDriver().getFullName());
            dto.setDriverPhone(v.getDriver().getPhone());
            dto.setRickshawNumber(v.getRickshawNumber());
            dto.setLatitude(lat);
            dto.setLongitude(lng);
            dto.setDistanceMeters(Math.round(distance));
            dto.setEtaSeconds(Math.round(distance / RICKSHAW_SPEED_MPS));
            dto.setCapacity(v.getCapacity());
            dto.setOccupiedSeats(occupied);
            dto.setAvailableSeats(Math.max(0, v.getCapacity() - occupied));
            dto.setOnTrip(trip.isPresent());
            if (v.getCurrentLocation() != null) {
                dto.setNearestLocationId(v.getCurrentLocation().getId());
                dto.setNearestLocationName(v.getCurrentLocation().getName());
            }
            dto.setLocationSource(v.getLocationSource());
            if (v.getLastLocationUpdate() != null) {
                dto.setLastUpdateSecondsAgo((nowMs - v.getLastLocationUpdate().toEpochMilli()) / 1000);
            }
            result.add(dto);
        }

        result.sort(Comparator.comparingDouble(NearbyDriverDto::getDistanceMeters));
        return result;
    }

    /**
     * Atomically accepts a passenger ride request.
     * Prevents overbooking using pessimistic locking on Vehicle and RideRequest.
     * Recalculates route feasibility before confirming.
     */
    @Transactional
    public RideRequestResponseDto acceptRideRequest(String requestId, String driverId) {
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + driverId));

        Vehicle vehicle = vehicleRepository.findByDriver(driver)
                .orElseThrow(() -> new IllegalArgumentException("No vehicle registered for driver: " + driverId));

        // 1. Lock RideRequest
        RideRequest request = rideRequestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Ride request not found: " + requestId));

        if (request.getStatus() != RideStatus.REQUESTED) {
            throw new IllegalStateException("Ride request is no longer available (current status: " + request.getStatus() + ")");
        }

        if (request.getDriver() != null && !request.getDriver().getId().equals(driverId)) {
            throw new IllegalStateException("This ride was requested from another rickshaw");
        }

        // 2. Lock Vehicle
        Vehicle lockedVehicle = vehicleRepository.findByIdWithLock(vehicle.getId())
                .orElseThrow(() -> new IllegalStateException("Vehicle record not found"));

        // 3. Find or create DriverTrip
        DriverTrip trip = driverTripRepository.findByVehicleAndStatusIn(lockedVehicle, List.of("AVAILABLE", "ARRIVING", "IN_PROGRESS"))
                .orElseGet(() -> {
                    DriverTrip newTrip = new DriverTrip(lockedVehicle, driver, lockedVehicle.getCurrentLocation());
                    return driverTripRepository.save(newTrip);
                });

        // 4. Server-Side Seat Limit & Overbooking Check
        int newOccupancy = trip.getOccupiedSeats() + request.getPassengerCount();
        if (newOccupancy > lockedVehicle.getCapacity()) {
            throw new IllegalStateException(String.format(
                    "Capacity exceeded: Vehicle capacity is %d, currently occupied %d, requested %d",
                    lockedVehicle.getCapacity(), trip.getOccupiedSeats(), request.getPassengerCount()
            ));
        }

        // 5. Recalculate route feasibility using Java DSA
        DijkstraShortestPath dijkstra = new DijkstraShortestPath(campusService.getCampusGraph());
        RouteResult toPickup = dijkstra.findShortestPath(
                lockedVehicle.getCurrentLocation() != null ? lockedVehicle.getCurrentLocation().getId() : "dairy_gate",
                request.getPickupLocation().getId()
        );
        RouteResult tripRoute = dijkstra.findShortestPath(
                request.getPickupLocation().getId(),
                request.getDestinationLocation().getId()
        );

        if (!toPickup.isReachable() || !tripRoute.isReachable()) {
            throw new IllegalStateException("Unreachable campus road segment detected for this route");
        }

        // 6. Update Trip and Stops
        trip.setOccupiedSeats(newOccupancy);
        if ("AVAILABLE".equals(trip.getStatus())) {
            trip.setStatus("ARRIVING");
        }

        int nextOrder = trip.getScheduledStops().size() + 1;
        TripStop pickupStop = new TripStop(trip, request.getPickupLocation(), nextOrder, request.getPassengerCount(), 0, request.getId());
        TripStop dropoffStop = new TripStop(trip, request.getDestinationLocation(), nextOrder + 1, 0, request.getPassengerCount(), request.getId());
        tripStopRepository.save(pickupStop);
        tripStopRepository.save(dropoffStop);

        trip.getScheduledStops().add(pickupStop);
        trip.getScheduledStops().add(dropoffStop);
        trip = driverTripRepository.save(trip);

        // 7. Update Request
        request.setDriver(driver);
        request.setVehicle(lockedVehicle);
        request.setDriverTrip(trip);
        request.setStatus(RideStatus.ACCEPTED);
        request.setAcceptedAt(Instant.now());
        request = rideRequestRepository.save(request);

        RideRequestResponseDto responseDto = RideRequestResponseDto.fromEntity(request);

        // 8. Real-Time Broadcast to Passenger
        notificationService.notifyRideStatusChanged(request.getId(), responseDto);

        return responseDto;
    }

    @Transactional
    public RideRequestResponseDto rejectRideRequest(String requestId, String driverId, String reason) {
        RideRequest request = rideRequestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Ride request not found: " + requestId));

        if (request.getStatus() != RideStatus.REQUESTED) {
            throw new IllegalStateException("Cannot reject request not in REQUESTED state: " + request.getStatus());
        }

        if (request.getDriver() == null) {
            // Broadcast request: hide it from this driver only, other drivers can still accept
            request.getRejectedByDriverIds().add(driverId);
            return RideRequestResponseDto.fromEntity(rideRequestRepository.save(request));
        }

        if (!request.getDriver().getId().equals(driverId)) {
            throw new AccessDeniedException("This ride was requested from another rickshaw");
        }

        request.setStatus(RideStatus.REJECTED);
        request.setRejectionReason(reason != null ? reason : "Driver unavailable");
        request = rideRequestRepository.save(request);

        RideRequestResponseDto responseDto = RideRequestResponseDto.fromEntity(request);

        // Broadcast to passenger
        notificationService.notifyRideStatusChanged(request.getId(), responseDto);

        return responseDto;
    }

    @Transactional
    public void updateLocation(String driverId, DriverLocationUpdate update) {
        Vehicle vehicle = vehicleRepository.findByDriverId(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver vehicle not found"));

        vehicle.setCurrentLatitude(update.getLatitude());
        vehicle.setCurrentLongitude(update.getLongitude());
        // Server clock, so a phone with a wrong clock does not look stale
        vehicle.setLastLocationUpdate(Instant.now());
        vehicle.setLocationSource(update.getLocationSource() != null ? update.getLocationSource() : "LIVE_PHONE");

        // Snap raw GPS to the nearest road-network vertex so Dijkstra routing keeps working
        String nearestId = update.getNearestLocationId();
        if (nearestId == null) {
            Vertex nearest = campusService.findNearestVertex(update.getLatitude(), update.getLongitude());
            nearestId = nearest != null ? nearest.getId() : null;
            update.setNearestLocationId(nearestId);
        }
        if (nearestId != null) {
            campusLocationRepository.findById(nearestId).ifPresent(vehicle::setCurrentLocation);
        }

        vehicleRepository.save(vehicle);

        // Notify active passengers connected to this driver's trips
        List<DriverTrip> activeTrips = driverTripRepository.findByVehicleAndStatusIn(
                vehicle, List.of("ARRIVING", "IN_PROGRESS")
        ).map(List::of).orElse(List.of());

        for (DriverTrip trip : activeTrips) {
            List<RideRequest> requests = rideRequestRepository.findByDriverTrip(trip);
            for (RideRequest req : requests) {
                if (!req.getStatus().isTerminal()) {
                    notificationService.notifyDriverLocation(req.getId(), driverId, update);
                }
            }
        }
    }

    @Transactional
    public DriverTrip updateTripStatus(String tripId, String driverId, String newStatus, String locationId) {
        DriverTrip trip = driverTripRepository.findByIdWithLock(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));

        if (!trip.getDriver().getId().equals(driverId)) {
            throw new AccessDeniedException("Driver does not own this trip");
        }

        trip.setStatus(newStatus.toUpperCase());
        if (locationId != null) {
            campusLocationRepository.findById(locationId).ifPresent(trip::setCurrentLocation);
        }

        if ("COMPLETED".equalsIgnoreCase(newStatus)) {
            trip.setEndTime(Instant.now());
            trip.setOccupiedSeats(0);

            // Complete associated ride requests
            List<RideRequest> requests = rideRequestRepository.findByDriverTrip(trip);
            for (RideRequest req : requests) {
                if (req.getStatus() == RideStatus.IN_PROGRESS || req.getStatus() == RideStatus.ACCEPTED) {
                    req.setStatus(RideStatus.COMPLETED);
                    req.setCompletedAt(Instant.now());
                    rideRequestRepository.save(req);
                    notificationService.notifyRideStatusChanged(req.getId(), RideRequestResponseDto.fromEntity(req));
                }
            }
        } else if ("IN_PROGRESS".equalsIgnoreCase(newStatus)) {
            List<RideRequest> requests = rideRequestRepository.findByDriverTrip(trip);
            for (RideRequest req : requests) {
                if (req.getStatus() == RideStatus.ACCEPTED || req.getStatus() == RideStatus.ARRIVING || req.getStatus() == RideStatus.PICKUP_REACHED) {
                    req.setStatus(RideStatus.IN_PROGRESS);
                    rideRequestRepository.save(req);
                    notificationService.notifyRideStatusChanged(req.getId(), RideRequestResponseDto.fromEntity(req));
                }
            }
        }

        return driverTripRepository.save(trip);
    }

    /**
     * Driver dashboard state. Built inside a transaction and flattened to plain maps/DTOs,
     * since DriverTrip has lazy collections that cannot be serialized after the session closes.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDriverStatus(String driverId) {
        Map<String, Object> status = new HashMap<>();
        vehicleRepository.findByDriverId(driverId).ifPresent(v -> {
            status.put("vehicle", v);
            driverTripRepository.findByVehicleAndStatusIn(v, List.of("ARRIVING", "IN_PROGRESS")).ifPresent(trip -> {
                status.put("activeTrip", toTripSummary(trip));
                status.put("activeRequests", rideRequestRepository.findByDriverTrip(trip).stream()
                        .filter(r -> !r.getStatus().isTerminal())
                        .map(RideRequestResponseDto::fromEntity)
                        .collect(Collectors.toList()));
            });
        });
        return status;
    }

    public static Map<String, Object> toTripSummary(DriverTrip trip) {
        Map<String, Object> tripMap = new HashMap<>();
        tripMap.put("id", trip.getId());
        tripMap.put("vehicleId", trip.getVehicle().getId());
        tripMap.put("driverId", trip.getDriver().getId());
        tripMap.put("status", trip.getStatus());
        tripMap.put("currentLocationId", trip.getCurrentLocation() != null ? trip.getCurrentLocation().getId() : null);
        tripMap.put("occupiedSeats", trip.getOccupiedSeats());
        tripMap.put("totalDistanceMeters", trip.getTotalDistanceMeters());
        return tripMap;
    }
}
