package service;

import model.DriverAccount;
import model.Location;
import model.PassengerAccount;
import model.Rickshaw;
import storage.CsvStorage;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

/** Registers and authenticates local passenger and driver accounts. */
public class AccountService {
    private static final String HASH_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int HASH_ITERATIONS = 210_000;
    private static final int HASH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final Pattern MOBILE_PATTERN = Pattern.compile("\\+?[0-9]{7,15}");
    private static final Pattern LOCAL_ACCOUNT_ID_PATTERN = Pattern.compile("LOCAL-[0-9a-fA-F-]{36}");

    private final CsvStorage storage;
    private final RideService rideService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Preferences preferences;
    private final Map<String, PassengerAccount> passengers = new LinkedHashMap<>();
    private final Map<String, DriverAccount> drivers = new LinkedHashMap<>();

    public AccountService(CsvStorage storage, RideService rideService) throws IOException {
        this.storage = storage;
        this.rideService = rideService;
        this.preferences = preferencesFor(storage.getDataDir());
        for (PassengerAccount account : storage.loadPassengerAccounts()) {
            if (isValidStoredAccount(account) && !isMobileRegistered(account.getMobileNumber())) {
                passengers.put(account.getMobileNumber(), account);
            } else {
                storage.addWarning("Skipped invalid or duplicate passenger account.");
            }
        }
        loadDriverAccounts();
    }

    public enum AccountRole {
        PASSENGER,
        DRIVER;

        @Override
        public String toString() {
            return this == PASSENGER ? "Passenger" : "Driver";
        }
    }

    public record AccountIdentity(String id, String name, AccountRole role) {
        @Override
        public String toString() {
            return name + " - " + role;
        }
    }

    public synchronized AccountIdentity createSimpleAccount(AccountRole role, String fullName, char[] password)
            throws IOException {
        if (role == null) {
            throw new IllegalArgumentException("Choose Passenger or Driver.");
        }
        String name = validateName(fullName);
        validateSimplePassword(password);
        String accountId = "LOCAL-" + UUID.randomUUID();

        if (role == AccountRole.PASSENGER) {
            PassengerAccount account = new PassengerAccount(name, accountId, hashPassword(password));
            passengers.put(accountId, account);
            try {
                storage.savePassengerAccounts(new ArrayList<>(passengers.values()));
            } catch (IOException e) {
                passengers.remove(accountId);
                throw e;
            }
        } else {
            List<Location> locations = rideService.getRouteService().getLocationsSorted();
            if (locations.size() < 2) {
                throw new IllegalStateException(
                        "At least two campus locations are needed to create a driver profile.");
            }
            String rickshawId = "CR-" + UUID.randomUUID();
            String initialLocationId = locations.get(0).getId();
            String destinationId = locations.get(1).getId();
            DriverAccount account = new DriverAccount(name, accountId, hashPassword(password), rickshawId,
                    initialLocationId, destinationId, 4, 4);
            Rickshaw rickshaw = new Rickshaw(rickshawId, name, initialLocationId, true,
                    4, 4, false, "", List.of());
            List<Rickshaw> existingRickshaws = new ArrayList<>(rideService.getRickshaws());
            drivers.put(accountId, account);
            try {
                storage.saveDriverAccounts(new ArrayList<>(drivers.values()));
                List<Rickshaw> updatedRickshaws = new ArrayList<>(existingRickshaws);
                updatedRickshaws.add(rickshaw);
                storage.saveRickshaws(updatedRickshaws);
                rideService.registerRickshaw(rickshaw);
            } catch (IOException | RuntimeException e) {
                drivers.remove(accountId);
                try {
                    storage.saveDriverAccounts(new ArrayList<>(drivers.values()));
                    storage.saveRickshaws(existingRickshaws);
                } catch (IOException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            }
        }

        AccountIdentity identity = new AccountIdentity(accountId, name, role);
        rememberLastAccount(identity);
        return identity;
    }

    public synchronized List<AccountIdentity> getAccounts(AccountRole role) {
        List<AccountIdentity> accounts = new ArrayList<>();
        if (role == AccountRole.PASSENGER) {
            passengers.values().forEach(account -> accounts.add(new AccountIdentity(
                    account.getMobileNumber(), account.getFullName(), AccountRole.PASSENGER)));
        } else if (role == AccountRole.DRIVER) {
            drivers.values().forEach(account -> accounts.add(new AccountIdentity(
                    account.getMobileNumber(), account.getFullName(), AccountRole.DRIVER)));
        }
        return List.copyOf(accounts);
    }

    public synchronized AccountIdentity getLastAccount() {
        String id = preferences.get("last_account_id", "");
        String roleName = preferences.get("last_account_role", "");
        return findAccount(id, parseRole(roleName));
    }

    public synchronized AccountIdentity authenticateAccount(AccountIdentity identity, char[] password) {
        if (identity == null || password == null || password.length == 0) {
            throw new IllegalArgumentException("Select an account and enter its password.");
        }
        Object account = identity.role() == AccountRole.PASSENGER
                ? passengers.get(identity.id()) : drivers.get(identity.id());
        authenticate(account, password);
        rememberLastAccount(identity);
        return identity;
    }

    public synchronized AccountIdentity verifyPasswordResetAccount(String fullName, String accountIdentifier) {
        String name = fullName == null ? "" : fullName.trim();
        String identifier = normalizeMobile(accountIdentifier);
        PassengerAccount passenger = findPassengerForReset(identifier);
        if (passenger != null && passenger.getFullName().equalsIgnoreCase(name)) {
            return new AccountIdentity(passenger.getMobileNumber(), passenger.getFullName(), AccountRole.PASSENGER);
        }
        DriverAccount driver = findDriverForReset(identifier);
        if (driver != null && driver.getFullName().equalsIgnoreCase(name)) {
            return new AccountIdentity(driver.getMobileNumber(), driver.getFullName(), AccountRole.DRIVER);
        }
        throw new IllegalArgumentException(
                "The registered name and mobile number/account ID did not match. Check the details and try again.");
    }

    public synchronized void resetPassword(String fullName, String accountIdentifier, char[] newPassword)
            throws IOException {
        validateResetPassword(newPassword);
        AccountIdentity verified = verifyPasswordResetAccount(fullName, accountIdentifier);
        String newHash = hashPassword(newPassword);
        if (verified.role() == AccountRole.PASSENGER) {
            PassengerAccount current = passengers.get(verified.id());
            PassengerAccount updated = new PassengerAccount(
                    current.getFullName(), current.getMobileNumber(), newHash);
            passengers.put(verified.id(), updated);
            try {
                storage.savePassengerAccounts(new ArrayList<>(passengers.values()));
            } catch (IOException e) {
                passengers.put(verified.id(), current);
                throw e;
            }
        } else {
            DriverAccount current = drivers.get(verified.id());
            DriverAccount updated = new DriverAccount(current.getFullName(), current.getMobileNumber(), newHash,
                    current.getRickshawId(), current.getInitialLocationId(), current.getDestinationId(),
                    current.getTotalSeats(), current.getAvailableSeats());
            drivers.put(verified.id(), updated);
            try {
                storage.saveDriverAccounts(new ArrayList<>(drivers.values()));
            } catch (IOException e) {
                drivers.put(verified.id(), current);
                throw e;
            }
        }
    }

    public synchronized void clearActiveSession() {
        preferences.remove("session_token");
        preferences.remove("session_account_id");
        preferences.remove("session_account_role");
        flushPreferences();
    }

    public synchronized PassengerAccount getPassengerByMobile(String mobileNumber) {
        return passengers.get(normalizeMobile(mobileNumber));
    }

    private void loadDriverAccounts() throws IOException {
        for (DriverAccount account : storage.loadDriverAccounts()) {
            if (isValidStoredAccount(account) && !isMobileRegistered(account.getMobileNumber())
                    && rideService.getRouteService().isValidLocation(account.getInitialLocationId())
                    && rideService.getRouteService().isValidLocation(account.getDestinationId())
                    && rideService.getRickshaw(account.getRickshawId()) != null) {
                drivers.put(account.getMobileNumber(), account);
                rideService.configureRickshawSeats(account.getRickshawId(),
                        account.getTotalSeats(), account.getAvailableSeats());
            } else {
                storage.addWarning("Skipped invalid or duplicate driver account.");
            }
        }
    }

    public synchronized PassengerAccount registerPassenger(String fullName, String mobileNumber, char[] password)
            throws IOException {
        String name = validateName(fullName);
        String mobile = validateMobile(mobileNumber);
        validateNewAccount(mobile, password);
        PassengerAccount account = new PassengerAccount(name, mobile, hashPassword(password));
        passengers.put(mobile, account);
        try {
            storage.savePassengerAccounts(new ArrayList<>(passengers.values()));
        } catch (IOException e) {
            passengers.remove(mobile);
            throw e;
        }
        return account;
    }

    public synchronized DriverAccount registerDriver(
            String fullName, String mobileNumber, char[] password, String rickshawId,
            String initialLocationId, String destinationId, int totalSeats, int availableSeats)
            throws IOException {
        String name = validateName(fullName);
        String mobile = validateMobile(mobileNumber);
        String rickshaw = requireText(rickshawId, "Enter a rickshaw ID.");
        if (rideService.getRickshaw(rickshaw) != null) {
            throw new IllegalArgumentException("That rickshaw ID is already registered.");
        }
        if (!rideService.getRouteService().isValidLocation(initialLocationId)
                || !rideService.getRouteService().isValidLocation(destinationId)) {
            throw new IllegalArgumentException("Choose valid initial and destination locations.");
        }
        if (initialLocationId.equals(destinationId)) {
            throw new IllegalArgumentException("Initial location and destination must be different.");
        }
        if (totalSeats < 1 || availableSeats < 0 || availableSeats > totalSeats) {
            throw new IllegalArgumentException("Seat counts must satisfy 1 <= total seats and 0 <= available <= total.");
        }
        validateNewAccount(mobile, password);

        DriverAccount account = new DriverAccount(name, mobile, hashPassword(password), rickshaw,
                initialLocationId, destinationId, totalSeats, availableSeats);
        drivers.put(mobile, account);
        try {
            storage.saveDriverAccounts(new ArrayList<>(drivers.values()));
            rideService.registerRickshaw(new Rickshaw(rickshaw, name, initialLocationId, true,
                    totalSeats, availableSeats, false, "", java.util.List.of()));
        } catch (IOException | RuntimeException e) {
            drivers.remove(mobile);
            try {
                storage.saveDriverAccounts(new ArrayList<>(drivers.values()));
            } catch (IOException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        }
        return account;
    }

    public synchronized PassengerAccount authenticatePassenger(String mobileNumber, char[] password) {
        return authenticate(passengers.get(normalizeMobile(mobileNumber)), password);
    }

    public synchronized DriverAccount authenticateDriver(String mobileNumber, char[] password) {
        return authenticate(drivers.get(normalizeMobile(mobileNumber)), password);
    }

    public synchronized DriverAccount getDriverByMobile(String mobileNumber) {
        return drivers.get(normalizeMobile(mobileNumber));
    }

    public synchronized DriverAccount updateDriverDestination(String mobileNumber, String destinationId)
            throws IOException {
        String mobile = normalizeMobile(mobileNumber);
        DriverAccount current = drivers.get(mobile);
        if (current == null) {
            throw new IllegalArgumentException("The driver account does not exist.");
        }
        if (!rideService.getRouteService().isValidLocation(destinationId)) {
            throw new IllegalArgumentException("Choose a valid destination.");
        }
        if (current.getInitialLocationId().equals(destinationId)) {
            throw new IllegalArgumentException("Destination must differ from the initial location.");
        }
        DriverAccount updated = new DriverAccount(current.getFullName(), current.getMobileNumber(),
                current.getPasswordHash(), current.getRickshawId(), current.getInitialLocationId(),
                destinationId, current.getTotalSeats(), current.getAvailableSeats());
        drivers.put(mobile, updated);
        try {
            storage.saveDriverAccounts(new ArrayList<>(drivers.values()));
        } catch (IOException e) {
            drivers.put(mobile, current);
            throw e;
        }
        return updated;
    }

    private <T> T authenticate(T account, char[] password) {
        if (account == null || password == null || password.length == 0) {
            throw new IllegalArgumentException("Invalid account or password.");
        }
        String storedHash = account instanceof PassengerAccount passenger
                ? passenger.getPasswordHash() : ((DriverAccount) account).getPasswordHash();
        if (!verifyPassword(password, storedHash)) {
            throw new IllegalArgumentException("Invalid account or password.");
        }
        return account;
    }

    private void validateNewAccount(String mobile, char[] password) {
        if (isMobileRegistered(mobile)) {
            throw new IllegalArgumentException("An account with that mobile number already exists.");
        }

        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters.");
        }
    }

    private static void validateSimplePassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Enter a password.");
        }
    }

    private boolean isMobileRegistered(String mobile) {
        return passengers.containsKey(mobile) || drivers.containsKey(mobile);
    }

    private PassengerAccount findPassengerForReset(String identifier) {
        PassengerAccount account = passengers.get(identifier);
        if (account != null || !isLocalAccountId(identifier)) {
            return account;
        }
        return passengers.values().stream()
                .filter(candidate -> candidate.getMobileNumber().equalsIgnoreCase(identifier))
                .findFirst().orElse(null);
    }

    private DriverAccount findDriverForReset(String identifier) {
        DriverAccount account = drivers.get(identifier);
        if (account != null || !isLocalAccountId(identifier)) {
            return account;
        }
        return drivers.values().stream()
                .filter(candidate -> candidate.getMobileNumber().equalsIgnoreCase(identifier))
                .findFirst().orElse(null);
    }

    private static void validateResetPassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Enter a new password.");
        }
        if (password.length < 10 || password.length > 128) {
            throw new IllegalArgumentException("Use 10 to 128 characters for your new password.");
        }
        boolean lowercase = false;
        boolean uppercase = false;
        boolean digit = false;
        boolean symbol = false;
        for (char character : password) {
            if (Character.isISOControl(character)) {
                throw new IllegalArgumentException("Password cannot contain control characters.");
            }
            lowercase |= Character.isLowerCase(character);
            uppercase |= Character.isUpperCase(character);
            digit |= Character.isDigit(character);
            symbol |= !Character.isLetterOrDigit(character);
        }
        if (!lowercase || !uppercase || !digit || !symbol) {
            throw new IllegalArgumentException(
                    "Include an uppercase letter, a lowercase letter, a number, and a symbol.");
        }
    }

    private static String validateName(String value) {
        String name = requireText(value, "Enter your full name.");
        if (name.length() > 100 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Full name must be at most 100 characters.");
        }
        return name;
    }

    private static String validateMobile(String value) {
        String mobile = normalizeMobile(value);
        if (!MOBILE_PATTERN.matcher(mobile).matches()) {
            throw new IllegalArgumentException("Enter a valid mobile number with 7 to 15 digits.");
        }
        return mobile;
    }

    private static boolean isLocalAccountId(String value) {
        return value != null
                && LOCAL_ACCOUNT_ID_PATTERN.matcher(value.toUpperCase(java.util.Locale.ROOT)).matches();
    }

    private static String normalizeMobile(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (isLocalAccountId(trimmed)) {
            return trimmed;
        }
        String mobile = trimmed.replaceAll("[\\s()-]", "");
        if (mobile.startsWith("+880") && mobile.length() == 14) {
            return "0" + mobile.substring(4);
        }
        if (mobile.startsWith("880") && mobile.length() == 13) {
            return "0" + mobile.substring(3);
        }
        return mobile;
    }

    private static String requireText(String value, String error) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(error);
        }
        return text;
    }

    private static boolean isValidStoredAccount(PassengerAccount account) {
        return account != null && !account.getFullName().isBlank()
                && isValidAccountId(account.getMobileNumber())
                && isEncodedHash(account.getPasswordHash());
    }

    private static boolean isValidStoredAccount(DriverAccount account) {
        return account != null && !account.getFullName().isBlank()
                && isValidAccountId(account.getMobileNumber())
                && isEncodedHash(account.getPasswordHash())
                && !account.getRickshawId().isBlank()
                && account.getTotalSeats() > 0
                && account.getAvailableSeats() >= 0
                && account.getAvailableSeats() <= account.getTotalSeats()
                && account.getInitialLocationId() != null
                && account.getDestinationId() != null;
    }

    private void rememberLastAccount(AccountIdentity identity) {
        preferences.put("last_account_id", identity.id());
        preferences.put("last_account_role", identity.role().name());
        flushPreferences();
    }

    private void flushPreferences() {
        try {
            preferences.flush();
        } catch (BackingStoreException e) {
            throw new IllegalStateException("Could not save the remembered account session.", e);
        }
    }

    private AccountIdentity findAccount(String id, AccountRole role) {
        if (id == null || id.isBlank() || role == null) {
            return null;
        }
        if (role == AccountRole.PASSENGER) {
            PassengerAccount account = passengers.get(id);
            return account == null ? null : new AccountIdentity(id, account.getFullName(), role);
        }
        DriverAccount account = drivers.get(id);
        return account == null ? null : new AccountIdentity(id, account.getFullName(), role);
    }

    private static AccountRole parseRole(String role) {
        try {
            return AccountRole.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private static boolean isValidAccountId(String id) {
        return id != null && (MOBILE_PATTERN.matcher(id).matches()
                || LOCAL_ACCOUNT_ID_PATTERN.matcher(id).matches());
    }

    private static Preferences preferencesFor(Path dataDirectory) {
        String path = dataDirectory.toAbsolutePath().normalize().toString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(path.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Preferences.userNodeForPackage(AccountService.class).node("sessions")
                    .node(Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not initialize remembered account sessions.", e);
        }
    }

    private String hashPassword(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] derived = deriveKey(password, salt, HASH_ITERATIONS);
        return HASH_ALGORITHM + "$" + HASH_ITERATIONS + "$"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(salt) + "$"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(derived);
    }

    private static boolean verifyPassword(char[] password, String encoded) {
        try {
            String[] parts = encoded.split("\\$", -1);
            if (parts.length != 4 || !HASH_ALGORITHM.equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 1 || iterations > 1_000_000) {
                return false;
            }
            byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[3]);
            byte[] actual = deriveKey(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isEncodedHash(String encoded) {
        if (encoded == null) {
            return false;
        }
        String[] parts = encoded.split("\\$", -1);
        if (parts.length != 4 || !HASH_ALGORITHM.equals(parts[0])) {
            return false;
        }
        try {
            return Integer.parseInt(parts[1]) >= 1
                    && Base64.getUrlDecoder().decode(parts[2]).length >= 16
                    && Base64.getUrlDecoder().decode(parts[3]).length == HASH_BITS / 8;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] deriveKey(char[] password, byte[] salt, int iterations) {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(HASH_ALGORITHM).generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is unavailable in this Java runtime.", e);
        } finally {
            keySpec.clearPassword();
        }
    }
}
