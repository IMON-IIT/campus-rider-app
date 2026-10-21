package com.jusmartride.ride;

import java.io.Serializable;
import java.time.Instant;

public class RideRequestResponseDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String passengerId;
    private String passengerName;
    private String passengerPhone;
    private String driverId;
    private String driverName;
    private String driverPhone;
    private String vehicleId;
    private String rickshawNumber;
    private String pickupLocationId;
    private String pickupLocationName;
    private double pickupLatitude;
    private double pickupLongitude;
    private String destinationLocationId;
    private String destinationLocationName;
    private double destinationLatitude;
    private double destinationLongitude;
    private int passengerCount;
    private RideStatus status;
    private Instant requestedAt;
    private Instant acceptedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private double estimatedFareBdt;
    private double detourRatio;
    private String rejectionReason;

    // Driver location snapshot for live tracking
    private Double driverCurrentLatitude;
    private Double driverCurrentLongitude;
    private String driverLocationSource;
    private Long driverLocationUpdatedAt;

    // Passenger's exact GPS pin, and (driver views only) road distance from driver to pickup
    private Double passengerLatitude;
    private Double passengerLongitude;
    private Double driverToPickupMeters;

    public RideRequestResponseDto() {
    }

    public static RideRequestResponseDto fromEntity(RideRequest r) {
        RideRequestResponseDto dto = new RideRequestResponseDto();
        dto.setId(r.getId());
        dto.setPassengerId(r.getPassenger().getId());
        dto.setPassengerName(r.getPassenger().getFullName());
        dto.setPassengerPhone(r.getPassenger().getPhone());

        if (r.getDriver() != null) {
            dto.setDriverId(r.getDriver().getId());
            dto.setDriverName(r.getDriver().getFullName());
            dto.setDriverPhone(r.getDriver().getPhone());
        }

        if (r.getVehicle() != null) {
            dto.setVehicleId(r.getVehicle().getId());
            dto.setRickshawNumber(r.getVehicle().getRickshawNumber());
            dto.setDriverCurrentLatitude(r.getVehicle().getCurrentLatitude());
            dto.setDriverCurrentLongitude(r.getVehicle().getCurrentLongitude());
            dto.setDriverLocationSource(r.getVehicle().getLocationSource());
            if (r.getVehicle().getLastLocationUpdate() != null) {
                dto.setDriverLocationUpdatedAt(r.getVehicle().getLastLocationUpdate().toEpochMilli());
            }
        }

        dto.setPassengerLatitude(r.getPassengerLatitude());
        dto.setPassengerLongitude(r.getPassengerLongitude());

        dto.setPickupLocationId(r.getPickupLocation().getId());
        dto.setPickupLocationName(r.getPickupLocation().getName());
        dto.setPickupLatitude(r.getPickupLocation().getLatitude());
        dto.setPickupLongitude(r.getPickupLocation().getLongitude());

        dto.setDestinationLocationId(r.getDestinationLocation().getId());
        dto.setDestinationLocationName(r.getDestinationLocation().getName());
        dto.setDestinationLatitude(r.getDestinationLocation().getLatitude());
        dto.setDestinationLongitude(r.getDestinationLocation().getLongitude());

        dto.setPassengerCount(r.getPassengerCount());
        dto.setStatus(r.getStatus());
        dto.setRequestedAt(r.getRequestedAt());
        dto.setAcceptedAt(r.getAcceptedAt());
        dto.setCompletedAt(r.getCompletedAt());
        dto.setCancelledAt(r.getCancelledAt());
        dto.setEstimatedFareBdt(r.getEstimatedFareBdt());
        dto.setDetourRatio(r.getDetourRatio());
        dto.setRejectionReason(r.getRejectionReason());

        return dto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPassengerId() {
        return passengerId;
    }

    public void setPassengerId(String passengerId) {
        this.passengerId = passengerId;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public void setPassengerName(String passengerName) {
        this.passengerName = passengerName;
    }

    public String getPassengerPhone() {
        return passengerPhone;
    }

    public void setPassengerPhone(String passengerPhone) {
        this.passengerPhone = passengerPhone;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getDriverPhone() {
        return driverPhone;
    }

    public void setDriverPhone(String driverPhone) {
        this.driverPhone = driverPhone;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getRickshawNumber() {
        return rickshawNumber;
    }

    public void setRickshawNumber(String rickshawNumber) {
        this.rickshawNumber = rickshawNumber;
    }

    public String getPickupLocationId() {
        return pickupLocationId;
    }

    public void setPickupLocationId(String pickupLocationId) {
        this.pickupLocationId = pickupLocationId;
    }

    public String getPickupLocationName() {
        return pickupLocationName;
    }

    public void setPickupLocationName(String pickupLocationName) {
        this.pickupLocationName = pickupLocationName;
    }

    public double getPickupLatitude() {
        return pickupLatitude;
    }

    public void setPickupLatitude(double pickupLatitude) {
        this.pickupLatitude = pickupLatitude;
    }

    public double getPickupLongitude() {
        return pickupLongitude;
    }

    public void setPickupLongitude(double pickupLongitude) {
        this.pickupLongitude = pickupLongitude;
    }

    public String getDestinationLocationId() {
        return destinationLocationId;
    }

    public void setDestinationLocationId(String destinationLocationId) {
        this.destinationLocationId = destinationLocationId;
    }

    public String getDestinationLocationName() {
        return destinationLocationName;
    }

    public void setDestinationLocationName(String destinationLocationName) {
        this.destinationLocationName = destinationLocationName;
    }

    public double getDestinationLatitude() {
        return destinationLatitude;
    }

    public void setDestinationLatitude(double destinationLatitude) {
        this.destinationLatitude = destinationLatitude;
    }

    public double getDestinationLongitude() {
        return destinationLongitude;
    }

    public void setDestinationLongitude(double destinationLongitude) {
        this.destinationLongitude = destinationLongitude;
    }

    public int getPassengerCount() {
        return passengerCount;
    }

    public void setPassengerCount(int passengerCount) {
        this.passengerCount = passengerCount;
    }

    public RideStatus getStatus() {
        return status;
    }

    public void setStatus(RideStatus status) {
        this.status = status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public double getEstimatedFareBdt() {
        return estimatedFareBdt;
    }

    public void setEstimatedFareBdt(double estimatedFareBdt) {
        this.estimatedFareBdt = estimatedFareBdt;
    }

    public double getDetourRatio() {
        return detourRatio;
    }

    public void setDetourRatio(double detourRatio) {
        this.detourRatio = detourRatio;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Double getDriverCurrentLatitude() {
        return driverCurrentLatitude;
    }

    public void setDriverCurrentLatitude(Double driverCurrentLatitude) {
        this.driverCurrentLatitude = driverCurrentLatitude;
    }

    public Double getDriverCurrentLongitude() {
        return driverCurrentLongitude;
    }

    public void setDriverCurrentLongitude(Double driverCurrentLongitude) {
        this.driverCurrentLongitude = driverCurrentLongitude;
    }

    public String getDriverLocationSource() {
        return driverLocationSource;
    }

    public void setDriverLocationSource(String driverLocationSource) {
        this.driverLocationSource = driverLocationSource;
    }

    public String getLocationSource() {
        return driverLocationSource;
    }

    public void setLocationSource(String locationSource) {
        this.driverLocationSource = locationSource;
    }

    public Long getDriverLocationUpdatedAt() {
        return driverLocationUpdatedAt;
    }

    public void setDriverLocationUpdatedAt(Long driverLocationUpdatedAt) {
        this.driverLocationUpdatedAt = driverLocationUpdatedAt;
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

    public Double getDriverToPickupMeters() {
        return driverToPickupMeters;
    }

    public void setDriverToPickupMeters(Double driverToPickupMeters) {
        this.driverToPickupMeters = driverToPickupMeters;
    }
}
