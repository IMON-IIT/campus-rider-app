package com.jusmartride.driver;

import jakarta.validation.constraints.NotBlank;

public class TripStatusUpdateDto {

    @NotBlank(message = "Status is required")
    private String status; // ARRIVING, PICKUP_REACHED, IN_PROGRESS, COMPLETED

    private String currentLocationId;

    public TripStatusUpdateDto() {
    }

    public TripStatusUpdateDto(String status, String currentLocationId) {
        this.status = status;
        this.currentLocationId = currentLocationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentLocationId() {
        return currentLocationId;
    }

    public void setCurrentLocationId(String currentLocationId) {
        this.currentLocationId = currentLocationId;
    }
}
