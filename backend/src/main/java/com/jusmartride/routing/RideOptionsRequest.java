package com.jusmartride.routing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class RideOptionsRequest {

    @NotBlank(message = "Pickup location ID is required")
    private String pickupId;

    @NotBlank(message = "Destination location ID is required")
    private String destinationId;

    @Min(value = 1, message = "Passenger count must be at least 1")
    private int passengerCount = 1;

    public RideOptionsRequest() {
    }

    public RideOptionsRequest(String pickupId, String destinationId, int passengerCount) {
        this.pickupId = pickupId;
        this.destinationId = destinationId;
        this.passengerCount = passengerCount;
    }

    public String getPickupId() {
        return pickupId;
    }

    public void setPickupId(String pickupId) {
        this.pickupId = pickupId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(String destinationId) {
        this.destinationId = destinationId;
    }

    public int getPassengerCount() {
        return passengerCount;
    }

    public void setPassengerCount(int passengerCount) {
        this.passengerCount = passengerCount;
    }
}
