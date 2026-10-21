package com.jusmartride.ride;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class CreateRideRequestDto {

    @NotBlank(message = "Pickup location ID is required")
    private String pickupLocationId;

    @NotBlank(message = "Destination location ID is required")
    private String destinationLocationId;

    @Min(value = 1, message = "Passenger count must be at least 1")
    private int passengerCount = 1;

    private String selectedDriverId;
    private String selectedVehicleId;
    private String selectedTripId;
    private Double estimatedFareBdt;
    private Double passengerLatitude;
    private Double passengerLongitude;

    public CreateRideRequestDto() {
    }

    public String getPickupLocationId() {
        return pickupLocationId;
    }

    public void setPickupLocationId(String pickupLocationId) {
        this.pickupLocationId = pickupLocationId;
    }

    public String getDestinationLocationId() {
        return destinationLocationId;
    }

    public void setDestinationLocationId(String destinationLocationId) {
        this.destinationLocationId = destinationLocationId;
    }

    public int getPassengerCount() {
        return passengerCount;
    }

    public void setPassengerCount(int passengerCount) {
        this.passengerCount = passengerCount;
    }

    public String getSelectedDriverId() {
        return selectedDriverId;
    }

    public void setSelectedDriverId(String selectedDriverId) {
        this.selectedDriverId = selectedDriverId;
    }

    public String getSelectedVehicleId() {
        return selectedVehicleId;
    }

    public void setSelectedVehicleId(String selectedVehicleId) {
        this.selectedVehicleId = selectedVehicleId;
    }

    public String getSelectedTripId() {
        return selectedTripId;
    }

    public void setSelectedTripId(String selectedTripId) {
        this.selectedTripId = selectedTripId;
    }

    public Double getEstimatedFareBdt() {
        return estimatedFareBdt;
    }

    public void setEstimatedFareBdt(Double estimatedFareBdt) {
        this.estimatedFareBdt = estimatedFareBdt;
    }

    public Double getPassengerLatitude() {
        return passengerLatitude;
    }

    public void setPassengerLatitude(Double passengerLatitude) {
        this.passengerLatitude = passengerLatitude;
    }

    public Double getPassengerLongitude() {
        return passengerLongitude;
    }

    public void setPassengerLongitude(Double passengerLongitude) {
        this.passengerLongitude = passengerLongitude;
    }
}
