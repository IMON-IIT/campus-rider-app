package com.jusmartride.ride;

import com.jusmartride.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rides/requests")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RideRequestResponseDto>> createRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateRideRequestDto dto) {
        RideRequestResponseDto created = rideService.createRideRequest(userDetails.getUsername(), dto);
        return ResponseEntity.ok(ApiResponse.ok("Ride requested successfully", created));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<RideRequestResponseDto>>> getMyRequests(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<RideRequestResponseDto> requests = rideService.getMyRideRequests(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(requests));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RideRequestResponseDto>> getRequestById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        RideRequestResponseDto request = rideService.getRideRequestById(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<RideRequestResponseDto>> cancelRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        RideRequestResponseDto cancelled = rideService.cancelRideRequest(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Ride request cancelled", cancelled));
    }
}
