package model;

/** A locally registered driver and the rickshaw associated with that account. */
public final class DriverAccount {
    private final String fullName;
    private final String mobileNumber;
    private final String passwordHash;
    private final String rickshawId;
    private final String initialLocationId;
    private final String destinationId;
    private final int totalSeats;
    private final int availableSeats;

    public DriverAccount(String fullName, String mobileNumber, String passwordHash,
                        String rickshawId, String initialLocationId, String destinationId,
                        int totalSeats, int availableSeats) {
        this.fullName = fullName;
        this.mobileNumber = mobileNumber;
        this.passwordHash = passwordHash;
        this.rickshawId = rickshawId;
        this.initialLocationId = initialLocationId;
        this.destinationId = destinationId;
        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
    }

    public String getFullName() {
        return fullName;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRickshawId() {
        return rickshawId;
    }

    public String getInitialLocationId() {
        return initialLocationId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }
}
