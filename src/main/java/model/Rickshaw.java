package model;

import java.util.ArrayList;
import java.util.List;

/** A campus rickshaw. It is either free (available) or serving one ride. */
public class Rickshaw {
    private final String id;
    private final String driverName;
    private String locationId;
    private boolean available;
    private int totalSeats = 4;
    private int availableSeats = 4;
    private boolean activeTrip;
    private String destinationId = "";
    private List<TripStop> itinerary = new ArrayList<>();
    private boolean seatConfigurationStored;

    public Rickshaw(String id, String driverName, String locationId, boolean available) {
        this.id = id;
        this.driverName = driverName;
        this.locationId = locationId;
        this.available = available;
    }

    public Rickshaw(String id, String driverName, String locationId, boolean available,
                    int totalSeats, int availableSeats, boolean activeTrip, String destinationId,
                    List<TripStop> itinerary) {
        this(id, driverName, locationId, available);
        setSeatCounts(totalSeats, availableSeats);
        this.activeTrip = activeTrip;
        this.destinationId = destinationId == null ? "" : destinationId;
        this.itinerary = new ArrayList<>(itinerary);
        this.seatConfigurationStored = true;
    }

    public String getId() {
        return id;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public void setSeatCounts(int totalSeats, int availableSeats) {
        if (totalSeats < 1 || availableSeats < 0 || availableSeats > totalSeats) {
            throw new IllegalArgumentException("Seat counts must satisfy 1 <= total and 0 <= available <= total.");
        }
        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
        this.seatConfigurationStored = true;
    }

    public boolean hasStoredSeatConfiguration() {
        return seatConfigurationStored;
    }

    public void setAvailableSeats(int availableSeats) {
        if (availableSeats < 0 || availableSeats > totalSeats) {
            throw new IllegalArgumentException("Available seats must be between zero and total seats.");
        }
        this.availableSeats = availableSeats;
        this.seatConfigurationStored = true;
    }

    public boolean hasActiveTrip() {
        return activeTrip;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public List<TripStop> getItinerary() {
        return List.copyOf(itinerary);
    }

    public void startTrip(String destinationId, List<TripStop> itinerary) {
        if (destinationId == null || destinationId.isBlank() || itinerary == null || itinerary.isEmpty()) {
            throw new IllegalArgumentException("A trip destination and route are required.");
        }
        this.destinationId = destinationId;
        this.itinerary = new ArrayList<>(itinerary);
        this.activeTrip = true;
        this.available = false;
    }

    public void setItinerary(List<TripStop> itinerary) {
        this.itinerary = new ArrayList<>(itinerary);
    }

    public void finishTrip() {
        activeTrip = false;
        destinationId = "";
        itinerary.clear();
        available = true;
        availableSeats = totalSeats;
    }
}
