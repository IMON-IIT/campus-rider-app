package model;

/** A locally registered passenger account. */
public final class PassengerAccount {
    private final String fullName;
    private final String mobileNumber;
    private final String passwordHash;

    public PassengerAccount(String fullName, String mobileNumber, String passwordHash) {
        this.fullName = fullName;
        this.mobileNumber = mobileNumber;
        this.passwordHash = passwordHash;
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
}
