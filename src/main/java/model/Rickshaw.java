package model;

/** A campus rickshaw. It is either free (available) or serving one ride. */
public class Rickshaw {
    private final String id;
    private final String driverName;
    private String locationId;
    private boolean available;

    public Rickshaw(String id, String driverName, String locationId, boolean available) {
        this.id = id;
        this.driverName = driverName;
        this.locationId = locationId;
        this.available = available;
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
}
