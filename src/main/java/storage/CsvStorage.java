package storage;

import model.Location;
import model.DriverAccount;
import model.PassengerType;
import model.PassengerAccount;
import model.RideRequest;
import model.RideStatus;
import model.Rickshaw;
import model.Road;
import model.TripStop;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads and writes the application's CSV files in the data folder.
 *
 *   locations.csv  id,name,category,rickshawStand,aliases   (aliases separated by ';')
 *   roads.csv      from,to,distanceMeters,roadName
 *   rickshaws.csv  id,driverName,locationId,status,totalSeats,availableSeats,activeTrip,destinationId,itinerary
 *   rides.csv      id,passengerName,passengerType,pickupId,destinationId,distanceMeters,status,rickshawId,
 *                  rejectedRickshawIds,passengerMobileNumber,requestedSeats,sharedTrip
 *
 * Rules:
 * - A missing file is created from the sample data bundled in the jar
 *   (rides.csv starts with only the header).
 * - A bad row is skipped and reported in getWarnings(); the original file is
 *   copied to "<name>.bak" so the user's data is never lost.
 * - Saving writes a temporary file first and then renames it, so a crash
 *   in the middle of saving cannot leave a half-written file.
 */
public class CsvStorage {
    public static final String LOCATIONS_FILE = "locations.csv";
    public static final String ROADS_FILE = "roads.csv";
    public static final String RICKSHAWS_FILE = "rickshaws.csv";
    public static final String RIDES_FILE = "rides.csv";
    public static final String PASSENGERS_FILE = "passengers.csv";
    public static final String DRIVERS_FILE = "drivers.csv";

    private static final String RIDES_HEADER =
            "id,passengerName,passengerType,pickupId,destinationId,distanceMeters,status,rickshawId,"
                    + "rejectedRickshawIds,passengerMobileNumber,requestedSeats,sharedTrip";
    private static final String EXTENDED_RICKSHAWS_HEADER =
            "id,driverName,locationId,status,totalSeats,availableSeats,activeTrip,destinationId,itinerary";
    private static final String PASSENGERS_HEADER = "fullName,mobileNumber,passwordHash";
    private static final String DRIVERS_HEADER =
            "fullName,mobileNumber,passwordHash,rickshawId,initialLocationId,destinationId,totalSeats,availableSeats";

    private final Path dataDir;
    private final List<String> warnings = new ArrayList<>();
    private boolean usingSampleCampus = false;

    public CsvStorage(Path dataDir) {
        this.dataDir = dataDir;
    }

    public Path getDataDir() {
        return dataDir;
    }

    /** Problems found while loading (skipped rows, missing files, ...). */
    public List<String> getWarnings() {
        return warnings;
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    // ---------------------------------------------------------------- loading

    public List<Location> loadLocations() throws IOException {
        List<Location> locations = parseLocations(readDataFile(LOCATIONS_FILE), LOCATIONS_FILE);
        if (locations.isEmpty()) {
            warnings.add(LOCATIONS_FILE + " has no valid locations. Using the built-in sample campus instead.");
            locations = parseLocations(readSample(LOCATIONS_FILE), "sample " + LOCATIONS_FILE);
            usingSampleCampus = true;
        }
        return locations;
    }

    /** Call after loadLocations(). Uses the sample roads if the sample locations were used, so the ids match. */
    public List<Road> loadRoads() throws IOException {
        if (usingSampleCampus) {
            return parseRoads(readSample(ROADS_FILE), "sample " + ROADS_FILE);
        }
        return parseRoads(readDataFile(ROADS_FILE), ROADS_FILE);
    }

    public List<Rickshaw> loadRickshaws() throws IOException {
        List<Rickshaw> rickshaws = new ArrayList<>();
        int skipped = 0;
        for (String[] row : rows(readDataFile(RICKSHAWS_FILE))) {
            if ((row.length != 4 && row.length != 9) || row[0].isEmpty()
                    || row[1].isEmpty() || row[2].isEmpty()) {
                skipped++;
                continue;
            }
            try {
                boolean available = !"BUSY".equalsIgnoreCase(row[3]);
                if (row.length == 4) {
                    rickshaws.add(new Rickshaw(row[0], row[1], row[2], available));
                } else {
                    List<TripStop> itinerary = parseItinerary(row[8]);
                    boolean activeTrip = parseBoolean(row[6]);
                    String destinationId = row[7];
                    if (activeTrip != !itinerary.isEmpty()
                            || (activeTrip && (destinationId.isBlank()
                            || itinerary.get(itinerary.size() - 1).type() != TripStop.Type.DESTINATION))
                            || (!activeTrip && !destinationId.isBlank())) {
                        throw new IllegalArgumentException("Invalid trip state.");
                    }
                    rickshaws.add(new Rickshaw(row[0], row[1], row[2], available,
                            Integer.parseInt(row[4]), Integer.parseInt(row[5]),
                            activeTrip, destinationId, itinerary));
                }
            } catch (IllegalArgumentException e) {
                skipped++;
            }
        }
        reportSkipped(RICKSHAWS_FILE, skipped);
        return rickshaws;
    }

    public List<RideRequest> loadRides() throws IOException {
        List<RideRequest> rides = new ArrayList<>();
        int skipped = 0;
        for (String[] row : rows(readDataFile(RIDES_FILE))) {
            try {
                if (row.length < 8 || row.length > 12) {
                    throw new IllegalArgumentException("wrong number of columns");
                }
                int id = Integer.parseInt(row[0]);
                double distance = Double.parseDouble(row[5]);
                if (id <= 0 || row[1].isEmpty() || !(distance >= 0)) {
                    throw new IllegalArgumentException("invalid value");
                }
                List<String> rejectedRickshawIds = row.length >= 9 && !row[8].isBlank()
                        ? Arrays.stream(row[8].split(";")).filter(idValue -> !idValue.isBlank()).toList()
                        : List.of();
                rides.add(new RideRequest(id, row[1], PassengerType.valueOf(row[2]), row[3], row[4],
                        distance, RideStatus.valueOf(row[6]), row[7], rejectedRickshawIds,
                        row.length >= 10 ? row[9] : "",
                        row.length >= 11 ? Integer.parseInt(row[10]) : 1,
                        row.length >= 12 && Boolean.parseBoolean(row[11])));
            } catch (IllegalArgumentException e) { // also covers NumberFormatException
                skipped++;
            }
        }
        reportSkipped(RIDES_FILE, skipped);
        return rides;
    }

    public List<PassengerAccount> loadPassengerAccounts() throws IOException {
        List<PassengerAccount> accounts = new ArrayList<>();
        for (String[] row : rows(readAccountFile(PASSENGERS_FILE, PASSENGERS_HEADER))) {
            if (row.length == 3) {
                accounts.add(new PassengerAccount(row[0], row[1], row[2]));
            } else {
                warnings.add(PASSENGERS_FILE + ": skipped a row with an invalid number of columns.");
            }
        }
        return accounts;
    }

    public List<DriverAccount> loadDriverAccounts() throws IOException {
        List<DriverAccount> accounts = new ArrayList<>();
        for (String[] row : rows(readAccountFile(DRIVERS_FILE, DRIVERS_HEADER))) {
            try {
                if (row.length != 8) {
                    throw new IllegalArgumentException("wrong number of columns");
                }
                accounts.add(new DriverAccount(row[0], row[1], row[2], row[3], row[4], row[5],
                        Integer.parseInt(row[6]), Integer.parseInt(row[7])));
            } catch (IllegalArgumentException e) {
                warnings.add(DRIVERS_FILE + ": skipped an invalid account row.");
            }
        }
        return accounts;
    }

    private List<Location> parseLocations(List<String> lines, String source) throws IOException {
        List<Location> locations = new ArrayList<>();
        int skipped = 0;
        for (String[] row : rows(lines)) {
            if (row.length < 4 || row.length > 5 || row[0].isEmpty() || row[1].isEmpty()) {
                skipped++;
                continue;
            }
            List<String> aliases = new ArrayList<>();
            if (row.length == 5) {
                for (String alias : row[4].split(";")) {
                    if (!alias.isBlank()) {
                        aliases.add(alias.trim());
                    }
                }
            }
            locations.add(new Location(row[0], row[1], row[2], Boolean.parseBoolean(row[3]), aliases));
        }
        reportSkipped(source, skipped);
        return locations;
    }

    private List<Road> parseRoads(List<String> lines, String source) throws IOException {
        List<Road> roads = new ArrayList<>();
        int skipped = 0;
        for (String[] row : rows(lines)) {
            try {
                if (row.length != 4) {
                    throw new IllegalArgumentException("wrong number of columns");
                }
                roads.add(new Road(row[0], row[1], Double.parseDouble(row[2]), row[3]));
            } catch (IllegalArgumentException e) {
                skipped++;
            }
        }
        reportSkipped(source, skipped);
        return roads;
    }

    /** Called when rows were skipped: keep a copy of the original file before it can be overwritten. */
    private void reportSkipped(String fileName, int skipped) throws IOException {
        if (skipped > 0) {
            warnings.add(fileName + ": skipped " + skipped + " invalid row(s).");
            backup(fileName);
        }
    }

    /** Copies data/<name> to data/<name>.bak (if the file exists). */
    public void backup(String fileName) throws IOException {
        Path file = dataDir.resolve(fileName);
        if (Files.exists(file)) {
            Path copy = dataDir.resolve(fileName + ".bak");
            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            warnings.add("A copy of the original " + fileName + " was saved as " + copy.getFileName() + ".");
        }
    }

    /** Splits each data line into trimmed columns. Skips the header, blank lines and '#' comments. */
    private static List<String[]> rows(List<String> lines) {
        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) { // line 0 is the header
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parsed = parseCsvLine(line);
            if (parsed.length == 0) {
                rows.add(parsed);
            } else {
                for (int c = 0; c < parsed.length; c++) {
                    parsed[c] = parsed[c].trim();
                }
                rows.add(parsed);
            }
        }
        return rows;
    }

    private static List<TripStop> parseItinerary(String encoded) {
        List<TripStop> stops = new ArrayList<>();
        if (encoded.isBlank()) {
            return stops;
        }
        for (String item : encoded.split(";")) {
            String[] fields = item.split(":", 3);
            if (fields.length != 3) {
                throw new IllegalArgumentException("Invalid itinerary.");
            }
            stops.add(new TripStop(Integer.parseInt(fields[1]), fields[2],
                    TripStop.Type.valueOf(fields[0])));
        }
        return stops;
    }

    private static boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean value.");
    }

    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(current);
            }
        }
        if (quoted) {
            return new String[0];
        }
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }

    private List<String> readAccountFile(String fileName, String header) throws IOException {
        Files.createDirectories(dataDir);
        Path file = dataDir.resolve(fileName);
        if (!Files.exists(file)) {
            writeLines(file, List.of(header));
            warnings.add(fileName + " was missing and has been created.");
        }
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    /** Reads a file from the data folder, creating it first if it does not exist. */
    private List<String> readDataFile(String fileName) throws IOException {
        Files.createDirectories(dataDir);
        Path file = dataDir.resolve(fileName);
        if (!Files.exists(file)) {
            List<String> initial = fileName.equals(RIDES_FILE) ? List.of(RIDES_HEADER) : readSample(fileName);
            writeLines(file, initial);
            warnings.add(fileName + " was missing and has been created.");
        }
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    /** Reads one of the sample files packaged in src/main/resources/sample. */
    private static List<String> readSample(String fileName) throws IOException {
        try (InputStream in = CsvStorage.class.getResourceAsStream("/sample/" + fileName)) {
            if (in == null) {
                throw new IOException("Sample file is missing from the application: " + fileName);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new ArrayList<>(Arrays.asList(text.split("\\R")));
        }
    }

    // ----------------------------------------------------------------- saving

    public void saveRickshaws(List<Rickshaw> rickshaws) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(EXTENDED_RICKSHAWS_HEADER);
        for (Rickshaw r : rickshaws) {
            lines.add(String.join(",", csvField(r.getId()), csvField(r.getDriverName()),
                    csvField(r.getLocationId()), r.isAvailable() ? "AVAILABLE" : "BUSY",
                    String.valueOf(r.getTotalSeats()), String.valueOf(r.getAvailableSeats()),
                    String.valueOf(r.hasActiveTrip()), csvField(r.getDestinationId()),
                    csvField(encodeItinerary(r.getItinerary()))));
        }
        writeLines(dataDir.resolve(RICKSHAWS_FILE), lines);
    }

    public void saveRides(List<RideRequest> rides) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(RIDES_HEADER);
        for (RideRequest r : rides) {
            lines.add(String.join(",", String.valueOf(r.getId()), csvField(r.getPassengerName()),
                    csvField(r.getPassengerType().name()), csvField(r.getPickupId()),
                    csvField(r.getDestinationId()), String.valueOf(r.getDistanceMeters()),
                    csvField(r.getStatus().name()), csvField(r.getRickshawId()),
                    csvField(String.join(";", r.getRejectedRickshawIds())),
                    csvField(r.getPassengerMobileNumber()), String.valueOf(r.getRequestedSeats()),
                    String.valueOf(r.isSharedTrip())));
        }
        writeLines(dataDir.resolve(RIDES_FILE), lines);
    }

    private static String encodeItinerary(List<TripStop> itinerary) {
        return itinerary.stream()
                .map(stop -> stop.type().name() + ":" + stop.rideId() + ":" + stop.locationId())
                .collect(java.util.stream.Collectors.joining(";"));
    }

    public void savePassengerAccounts(List<PassengerAccount> accounts) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(PASSENGERS_HEADER);
        for (PassengerAccount account : accounts) {
            lines.add(String.join(",", csvField(account.getFullName()), csvField(account.getMobileNumber()),
                    csvField(account.getPasswordHash())));
        }
        writeLines(dataDir.resolve(PASSENGERS_FILE), lines);
    }

    public void saveDriverAccounts(List<DriverAccount> accounts) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(DRIVERS_HEADER);
        for (DriverAccount account : accounts) {
            lines.add(String.join(",", csvField(account.getFullName()), csvField(account.getMobileNumber()),
                    csvField(account.getPasswordHash()), csvField(account.getRickshawId()),
                    csvField(account.getInitialLocationId()), csvField(account.getDestinationId()),
                    String.valueOf(account.getTotalSeats()), String.valueOf(account.getAvailableSeats())));
        }
        writeLines(dataDir.resolve(DRIVERS_FILE), lines);
    }

    private static String csvField(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** Writes to "<file>.tmp" and then renames it over the real file. */
    private void writeLines(Path file, List<String> lines) throws IOException {
        Files.createDirectories(dataDir);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
