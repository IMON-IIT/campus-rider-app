package com.jusmartride.ride;

import com.jusmartride.campus.CampusLocation;
import com.jusmartride.user.User;
import com.jusmartride.vehicle.Vehicle;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "ride_requests")
public class RideRequest {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "passenger_id", nullable = false)
    private User passenger;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "driver_id")
    private User driver;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_trip_id")
    private DriverTrip driverTrip;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pickup_location_id", nullable = false)
    private CampusLocation pickupLocation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "destination_location_id", nullable = false)
    private CampusLocation destinationLocation;

    @Column(name = "passenger_count", nullable = false)
    private int passengerCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RideStatus status = RideStatus.REQUESTED;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "detour_ratio")
    private double detourRatio = 0.0;

    @Column(name = "estimated_fare_bdt")
    private double estimatedFareBdt = 20.0;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // Exact GPS pin of the passenger when requesting (pickup location is the snapped campus vertex)
    @Column(name = "passenger_latitude")
    private Double passengerLatitude;

    @Column(name = "passenger_longitude")
    private Double passengerLongitude;

    // Drivers who declined this broadcast request; it stays hidden from them
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ride_request_rejections", joinColumns = @JoinColumn(name = "request_id"))
    @Column(name = "driver_id", length = 36)
    private Set<String> rejectedByDriverIds = new HashSet<>();

    public RideRequest() {
    }

    public RideRequest(User passenger, CampusLocation pickupLocation, CampusLocation destinationLocation,
                       int passengerCount, double estimatedFareBdt) {
        this.id = UUID.randomUUID().toString();
        this.passenger = passenger;
        this.pickupLocation = pickupLocation;
        this.destinationLocation = destinationLocation;
        this.passengerCount = passengerCount > 0 ? passengerCount : 1;
        this.status = RideStatus.REQUESTED;
        this.requestedAt = Instant.now();
        this.estimatedFareBdt = estimatedFareBdt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public User getPassenger() {
        return passenger;
    }

    public void setPassenger(User passenger) {
        this.passenger = passenger;
    }

    public User getDriver() {
        return driver;
    }

    public void setDriver(User driver) {
        this.driver = driver;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public DriverTrip getDriverTrip() {
        return driverTrip;
    }

    public void setDriverTrip(DriverTrip driverTrip) {
        this.driverTrip = driverTrip;
    }

    public CampusLocation getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(CampusLocation pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public CampusLocation getDestinationLocation() {
        return destinationLocation;
    }

    public void setDestinationLocation(CampusLocation destinationLocation) {
        this.destinationLocation = destinationLocation;
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

    public double getDetourRatio() {
        return detourRatio;
    }

    public void setDetourRatio(double detourRatio) {
        this.detourRatio = detourRatio;
    }

    public double getEstimatedFareBdt() {
        return estimatedFareBdt;
    }

    public void setEstimatedFareBdt(double estimatedFareBdt) {
        this.estimatedFareBdt = estimatedFareBdt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
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

    public Set<String> getRejectedByDriverIds() {
        return rejectedByDriverIds;
    }

    public void setRejectedByDriverIds(Set<String> rejectedByDriverIds) {
        this.rejectedByDriverIds = rejectedByDriverIds;
    }
}
