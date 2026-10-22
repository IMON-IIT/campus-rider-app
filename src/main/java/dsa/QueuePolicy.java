package dsa;

/** Order in which pending ride requests are processed. */
public enum QueuePolicy {
    FIFO("First come, first served"),
    PRIORITY("Emergency first, then staff, then students (same type: earliest first)");

    private final String description;

    QueuePolicy(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
