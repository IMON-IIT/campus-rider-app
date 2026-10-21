package com.jusmartride.driver;

import com.jusmartride.common.ApiResponse;
import com.jusmartride.ride.DriverTrip;
import com.jusmartride.ride.RideRequestResponseDto;
import com.jusmartride.tracking.DriverLocationUpdate;
import com.jusmartride.vehicle.Vehicle;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/driver")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping("/availability")
    public ResponseEntity<ApiResponse<Vehicle>> setAvailability(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody AvailabilityDto dto) {
        Vehicle vehicle = driverService.setAvailability(userDetails.getUsername(), dto.isOnline(), dto.getCurrentLocationId());
        return ResponseEntity.ok(ApiResponse.ok("Availability updated successfully", vehicle));
    }

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<List<RideRequestResponseDto>>> getIncomingRequests(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<RideRequestResponseDto> requests = driverService.getIncomingRequests(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(requests));
    }

    @PostMapping("/requests/{id}/accept")
    public ResponseEntity<ApiResponse<RideRequestResponseDto>> acceptRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        RideRequestResponseDto result = driverService.acceptRideRequest(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Ride accepted successfully", result));
    }

    @PostMapping("/requests/{id}/reject")
    public ResponseEntity<ApiResponse<RideRequestResponseDto>> rejectRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @RequestBody(required = false) RejectRequestDto dto) {
        String reason = (dto != null) ? dto.getReason() : "Driver unavailable";
        RideRequestResponseDto result = driverService.rejectRideRequest(id, userDetails.getUsername(), reason);
        return ResponseEntity.ok(ApiResponse.ok("Ride rejected", result));
    }

    @PostMapping("/location")
    public ResponseEntity<ApiResponse<Void>> updateLocation(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody DriverLocationUpdate update) {
        driverService.updateLocation(userDetails.getUsername(), update);
        return ResponseEntity.ok(ApiResponse.ok("Location updated", null));
    }

    @PostMapping("/trips/{tripId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateTripStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String tripId,
            @RequestBody TripStatusUpdateDto dto) {
        DriverTrip trip = driverService.updateTripStatus(tripId, userDetails.getUsername(), dto.getStatus(), dto.getCurrentLocationId());
        return ResponseEntity.ok(ApiResponse.ok("Trip status updated", DriverService.toTripSummary(trip)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDriverStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(driverService.getDriverStatus(userDetails.getUsername())));
    }
}
