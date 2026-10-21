package com.jusmartride.ride;

import com.jusmartride.algorithms.DijkstraShortestPath;
import com.jusmartride.algorithms.RouteResult;
import com.jusmartride.campus.CampusLocation;
import com.jusmartride.campus.CampusLocationRepository;
import com.jusmartride.campus.CampusService;
import com.jusmartride.common.GeoUtils;
import com.jusmartride.notification.NotificationService;
import com.jusmartride.user.Role;
import com.jusmartride.user.User;
import com.jusmartride.user.UserRepository;
import com.jusmartride.vehicle.Vehicle;
import com.jusmartride.vehicle.VehicleRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RideService {

    private final RideRequestRepository rideRequestRepository;
    private final DriverTripRepository driverTripRepository;
    private final CampusLocationRepository campusLocationRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final NotificationService notificationService;
    private final CampusService campusService;
    private final PendingRideQueue pendingRideQueue = new PendingRideQueue();

    public RideService(RideRequestRepository rideRequestRepository,
                       DriverTripRepository driverTripRepository,
                       CampusLocationRepository campusLocationRepository,
                       UserRepository userRepository,
                       VehicleRepository vehicleRepository,
                       NotificationService notificationService,
                       CampusService campusService) {
        this.rideRequestRepository = rideRequestRepository;
        this.driverTripRepository = driverTripRepository;
        this.campusLocationRepository = campusLocationRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.notificationService = notificationService;
        this.campusService = campusService;
    }

    public PendingRideQueue getPendingRideQueue() {
        return pendingRideQueue;
    }

    @Transactional
    public RideRequestResponseDto createRideRequest(String passengerId, CreateRideRequestDto dto) {
        User passenger = userRepository.findById(passengerId)
                .orElseThrow(() -> new IllegalArgumentException("Passenger not found with id: " + passengerId));

        CampusLocation pickup = campusLocationRepository.findById(dto.getPickupLocationId())
                .orElseThrow(() -> new IllegalArgumentException("Pickup location not found: " + dto.getPickupLocationId()));

        CampusLocation destination = campusLocationRepository.findById(dto.getDestinationLocationId())
                .orElseThrow(() -> new IllegalArgumentException("Destination location not found: " + dto.getDestinationLocationId()));

        if (pickup.getId().equals(destination.getId())) {
            throw new IllegalArgumentException("Pickup and destination locations cannot be identical");
        }

        // One live request per passenger, like Uber/Pathao
        boolean hasActiveRide = rideRequestRepository.findByPassengerOrderByRequestedAtDesc(passenger).stream()
                .anyMatch(r -> !r.getStatus().isTerminal());
        if (hasActiveRide) {
            throw new IllegalStateException("You already have an active ride. Cancel or finish it before requesting another.");
        }

        // Fare is computed on the server from the Dijkstra road distance
        RouteResult route = new DijkstraShortestPath(campusService.getCampusGraph())
                .findShortestPath(pickup.getId(), destination.getId());
        if (!route.isReachable()) {
            throw new IllegalArgumentException("No campus road connects " + pickup.getName() + " and " + destination.getName());
        }
        double fare = GeoUtils.estimateFareBdt(route.getTotalDistanceMeters());

        RideRequest request = new RideRequest(passenger, pickup, destination, dto.getPassengerCount(), fare);
        request.setPassengerLatitude(dto.getPassengerLatitude());
        request.setPassengerLongitude(dto.getPassengerLongitude());

        // Targeted driver / vehicle if the passenger picked one on the map; otherwise broadcast to all online drivers
        if (dto.getSelectedVehicleId() != null && !dto.getSelectedVehicleId().isBlank()) {
            Vehicle vehicle = vehicleRepository.findById(dto.getSelectedVehicleId())
                    .orElseThrow(() -> new IllegalArgumentException("Selected rickshaw not found"));
            if (!vehicle.isOnline()) {
                throw new IllegalStateException("Rickshaw " + vehicle.getRickshawNumber() + " just went offline. Pick another one.");
            }
            request.setVehicle(vehicle);
            request.setDriver(vehicle.getDriver());
        } else if (dto.getSelectedDriverId() != null && !dto.getSelectedDriverId().isBlank()) {
            userRepository.findById(dto.getSelectedDriverId()).ifPresent(request::setDriver);
            vehicleRepository.findByDriverId(dto.getSelectedDriverId()).ifPresent(request::setVehicle);
        }

        request = rideRequestRepository.save(request);

        // Fairness queue
        pendingRideQueue.enqueue(request.getId());

        RideRequestResponseDto responseDto = RideRequestResponseDto.fromEntity(request);

        // Notify specific driver or all online drivers
        if (request.getDriver() != null) {
            notificationService.notifyDriverNewRequest(request.getDriver().getId(), responseDto);
        } else {
            notificationService.notifyBroadcastRequest(responseDto);
        }

        return responseDto;
    }

    @Transactional(readOnly = true)
    public List<RideRequestResponseDto> getMyRideRequests(String passengerId) {
        User passenger = userRepository.findById(passengerId)
                .orElseThrow(() -> new IllegalArgumentException("Passenger not found: " + passengerId));

        return rideRequestRepository.findByPassengerOrderByRequestedAtDesc(passenger).stream()
                .map(RideRequestResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RideRequestResponseDto getRideRequestById(String requestId, String userId) {
        RideRequest request = rideRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Ride request not found: " + requestId));

        // Ownership check: must be the passenger or assigned driver or admin
        boolean isPassenger = request.getPassenger().getId().equals(userId);
        boolean isDriver = request.getDriver() != null && request.getDriver().getId().equals(userId);
        User user = userRepository.findById(userId).orElse(null);
        boolean isAdmin = user != null && user.getRole() == Role.ROLE_ADMIN;

        if (!isPassenger && !isDriver && !isAdmin) {
            throw new AccessDeniedException("You are not authorized to view this ride request");
        }

        return RideRequestResponseDto.fromEntity(request);
    }

    @Transactional
    public RideRequestResponseDto cancelRideRequest(String requestId, String passengerId) {
        RideRequest request = rideRequestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Ride request not found: " + requestId));

        if (!request.getPassenger().getId().equals(passengerId)) {
            throw new AccessDeniedException("Only the requesting passenger can cancel this ride request");
        }

        if (request.getStatus().isTerminal()) {
            throw new IllegalStateException("Cannot cancel request already in terminal state: " + request.getStatus());
        }

        if (!request.getStatus().canTransitionTo(RideStatus.CANCELLED)) {
            throw new IllegalStateException("Cannot cancel ride request from current status: " + request.getStatus());
        }

        // If trip was already assigned, release the seats
        if (request.getDriverTrip() != null) {
            DriverTrip trip = request.getDriverTrip();
            trip.setOccupiedSeats(Math.max(0, trip.getOccupiedSeats() - request.getPassengerCount()));
            // Close the trip if this was its last passenger
            boolean othersRemain = rideRequestRepository.findByDriverTrip(trip).stream()
                    .anyMatch(r -> !r.getId().equals(requestId) && !r.getStatus().isTerminal());
            if (!othersRemain) {
                trip.setStatus("COMPLETED");
                trip.setEndTime(Instant.now());
            }
            driverTripRepository.save(trip);
        }

        request.setStatus(RideStatus.CANCELLED);
        request.setCancelledAt(Instant.now());
        request = rideRequestRepository.save(request);

        pendingRideQueue.remove(request.getId());

        RideRequestResponseDto responseDto = RideRequestResponseDto.fromEntity(request);

        // Notify passenger and driver via WebSocket
        notificationService.notifyRideStatusChanged(request.getId(), responseDto);
        if (request.getDriver() != null) {
            notificationService.notifyDriverNewRequest(request.getDriver().getId(), responseDto);
        }

        return responseDto;
    }
}
