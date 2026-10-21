package com.jusmartride.driver;

import com.jusmartride.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Passenger-facing driver discovery (separate from /api/v1/driver/**, which is driver-only).
 */
@RestController
@RequestMapping("/api/v1/drivers")
public class NearbyDriverController {

    private final DriverService driverService;

    public NearbyDriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<NearbyDriverDto>>> getNearbyDrivers(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false, defaultValue = "3000") double radius) {
        return ResponseEntity.ok(ApiResponse.ok(driverService.findNearbyDrivers(lat, lng, radius)));
    }
}
