package com.jusmartride.ride;

import com.jusmartride.campus.CampusLocation;
import com.jusmartride.user.User;
import com.jusmartride.vehicle.Vehicle;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "driver_trips")
public class DriverTrip {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @Column(nullable = false, length = 50)
    private String status = "AVAILABLE"; // AVAILABLE, ARRIVING, IN_PROGRESS, COMPLETED, CANCELLED

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "current_location_id")
    private CampusLocation currentLocation;

    @Column(name = "occupied_seats", nullable = false)
    private int occupiedSeats = 0;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "total_distance_meters")
    private double totalDistanceMeters = 0.0;

    @OneToMany(mappedBy = "driverTrip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stopOrder ASC")
    private List<TripStop> scheduledStops = new ArrayList<>();

    public DriverTrip() {
    }

    public DriverTrip(Vehicle vehicle, User driver, CampusLocation currentLocation) {
        this.id = UUID.randomUUID().toString();
        this.vehicle = vehicle;
        this.driver = driver;
        this.currentLocation = currentLocation;
        this.status = "AVAILABLE";
        this.occupiedSeats = 0;
        this.startTime = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public User getDriver() {
        return driver;
    }

    public void setDriver(User driver) {
        this.driver = driver;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public CampusLocation getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(CampusLocation currentLocation) {
        this.currentLocation = currentLocation;
    }

    public int getOccupiedSeats() {
        return occupiedSeats;
    }

    public void setOccupiedSeats(int occupiedSeats) {
        this.occupiedSeats = Math.max(0, occupiedSeats);
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public void setTotalDistanceMeters(double totalDistanceMeters) {
        this.totalDistanceMeters = totalDistanceMeters;
    }

    public List<TripStop> getScheduledStops() {
        return scheduledStops;
    }

    public void setScheduledStops(List<TripStop> scheduledStops) {
        this.scheduledStops = scheduledStops;
    }
}
