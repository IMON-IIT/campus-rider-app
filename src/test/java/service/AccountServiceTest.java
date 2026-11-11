package service;

import model.DriverAccount;
import model.PassengerAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import storage.CsvStorage;

import java.nio.file.Files;
import java.nio.file.Path;

import service.AccountService.AccountIdentity;
import service.AccountService.AccountRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountServiceTest {
    @TempDir
    Path dir;

    @Test
    void passengerRegistrationHashesPasswordAndPersistsAcrossRestart() throws Exception {
        TestContext context = start(dir);
        char[] password = "passenger-secret".toCharArray();
        PassengerAccount registered = context.accounts.registerPassenger(
                "Nusrat, Jahan", "+880 1712-345678", password);

        assertEquals("Nusrat, Jahan", registered.getFullName());
        assertEquals("01712345678", registered.getMobileNumber());
        assertNotEquals("passenger-secret", registered.getPasswordHash());
        assertTrue(Files.readString(dir.resolve(CsvStorage.PASSENGERS_FILE)).contains("PBKDF2WithHmacSHA256"));
        assertTrue(!Files.readString(dir.resolve(CsvStorage.PASSENGERS_FILE)).contains("passenger-secret"));
        context.rides.submitRideRequest("Nusrat, Jahan", model.PassengerType.STUDENT,
                "bottola", "dairy_gate", registered.getMobileNumber());

        TestContext restarted = start(dir);
        PassengerAccount loggedIn = restarted.accounts.authenticatePassenger(
                " +880 1712 345678 ", "passenger-secret".toCharArray());
        assertEquals(registered.getMobileNumber(), loggedIn.getMobileNumber());
        assertEquals(registered.getMobileNumber(),
                restarted.rides.getLatestRideForPassenger(loggedIn.getMobileNumber()).getPassengerMobileNumber());
        assertThrows(IllegalArgumentException.class, () -> restarted.accounts.authenticatePassenger(
                registered.getMobileNumber(), "incorrect-password".toCharArray()));
    }

    @Test
    void driverRegistrationLinksRickshawAndPersistsAccountFields() throws Exception {
        TestContext context = start(dir);
        DriverAccount registered = context.accounts.registerDriver(
                "Rana, Mia", "01712345678", "driver-secret".toCharArray(),
                "CAMPUS-RICK-900", "bottola", "dairy_gate", 4, 3);

        assertEquals("Rana, Mia", context.rides.getRickshaw("CAMPUS-RICK-900").getDriverName());
        assertEquals("bottola", registered.getInitialLocationId());
        assertEquals("dairy_gate", registered.getDestinationId());
        assertEquals(4, registered.getTotalSeats());
        assertEquals(3, registered.getAvailableSeats());
        registered = context.accounts.updateDriverDestination("01712345678", "cse_building");
        assertEquals("cse_building", registered.getDestinationId());

        TestContext restarted = start(dir);
        DriverAccount loggedIn = restarted.accounts.authenticateDriver(
                "01712345678", "driver-secret".toCharArray());
        assertNotNull(restarted.rides.getRickshaw(loggedIn.getRickshawId()));
        assertEquals("CAMPUS-RICK-900", loggedIn.getRickshawId());
        assertEquals(4, loggedIn.getTotalSeats());
        assertEquals("cse_building", loggedIn.getDestinationId());
        assertThrows(IllegalArgumentException.class, () -> restarted.accounts.authenticateDriver(
                "01712345678", "wrong-secret".toCharArray()));
    }

    @Test
    void mobileNumbersAreUniqueAcrossPassengerAndDriverAccounts() throws Exception {
        TestContext context = start(dir);
        context.accounts.registerPassenger("Passenger One", "01712345678", "password-one".toCharArray());

        assertThrows(IllegalArgumentException.class, () -> context.accounts.registerPassenger(
                "Passenger Two", "+8801712345678", "password-two".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> context.accounts.registerDriver(
                "Driver One", "01712345678", "password-three".toCharArray(),
                "CAMPUS-RICK-901", "bottola", "dairy_gate", 3, 3));
    }

    @Test
    void invalidDriverRegistrationDoesNotCreateAnAccountOrRickshaw() throws Exception {
        TestContext context = start(dir);

        assertThrows(IllegalArgumentException.class, () -> context.accounts.registerDriver(
                "Driver One", "01712345678", "password-three".toCharArray(),
                "CAMPUS-RICK-901", "bottola", "dairy_gate", 2, 3));
        assertNull(context.accounts.getDriverByMobile("01712345678"));
        assertNull(context.rides.getRickshaw("CAMPUS-RICK-901"));
    }

    @Test
    void simpleAccountPersistsNameAndRequiresPasswordAfterRestartAndLogout() throws Exception {
        TestContext context = start(dir);
        String password = "raw-password-must-not-be-saved-41927";
        AccountIdentity created = context.accounts.createSimpleAccount(
                AccountRole.PASSENGER, "Simple Passenger", password.toCharArray());

        assertEquals("Simple Passenger", created.name());
        assertTrue(created.id().startsWith("LOCAL-"));
        String csv = Files.readString(dir.resolve(CsvStorage.PASSENGERS_FILE));
        assertTrue(csv.contains("PBKDF2WithHmacSHA256"));
        assertNotEquals(password, context.accounts.getPassengerByMobile(created.id()).getPasswordHash());
        assertTrue(!csv.contains(password), "CSV storage must contain only the PBKDF2 hash.");
        assertThrows(IllegalArgumentException.class, () ->
                context.accounts.createSimpleAccount(AccountRole.PASSENGER, " ", "password".toCharArray()));
        assertThrows(IllegalArgumentException.class, () ->
                context.accounts.createSimpleAccount(AccountRole.PASSENGER, "Empty Password", new char[0]));

        AccountIdentity account = context.accounts.getAccounts(AccountRole.PASSENGER).get(0);
        assertEquals(account, context.accounts.getLastAccount());
        assertThrows(IllegalArgumentException.class, () ->
                context.accounts.authenticateAccount(account, "incorrect-password".toCharArray()));
        context.accounts.authenticateAccount(account, password.toCharArray());

        TestContext restarted = start(dir);
        assertEquals(account, restarted.accounts.getLastAccount(),
                "The saved name and role should be available on the login screen after restart.");
        assertThrows(IllegalArgumentException.class, () ->
                restarted.accounts.authenticateAccount(account, "incorrect-password".toCharArray()));
        assertEquals(account, restarted.accounts.authenticateAccount(account, password.toCharArray()));
        restarted.accounts.clearActiveSession();
        assertEquals(account, restarted.accounts.getLastAccount(),
                "Logout clears active session state but keeps the account name selected.");
    }

    @Test
    void simpleAccountAcceptsAnyNonEmptyPassword() throws Exception {
        TestContext context = start(dir);
        AccountIdentity account = context.accounts.createSimpleAccount(
                AccountRole.PASSENGER, "Short Password", "x".toCharArray());

        assertEquals(account, context.accounts.authenticateAccount(account, "x".toCharArray()));
    }

    @Test
    void simpleDriverAccountGetsPersistedRickshawWithoutExtraRegistrationFields() throws Exception {
        TestContext context = start(dir);
        AccountIdentity identity = context.accounts.createSimpleAccount(
                AccountRole.DRIVER, "Simple Driver", "driver-password".toCharArray());
        DriverAccount driver = context.accounts.getDriverByMobile(identity.id());

        assertNotNull(driver);
        assertNotNull(context.rides.getRickshaw(driver.getRickshawId()));
        assertTrue(driver.getRickshawId().startsWith("CR-"));

        TestContext restarted = start(dir);
        assertEquals(identity, restarted.accounts.getLastAccount());
        assertNotNull(restarted.rides.getRickshaw(driver.getRickshawId()));
        assertThrows(IllegalArgumentException.class, () ->
                restarted.accounts.authenticateAccount(identity, "incorrect".toCharArray()));
        assertEquals(identity, restarted.accounts.authenticateAccount(
                identity, "driver-password".toCharArray()));
    }

    @Test
    void passengerPasswordResetChangesOnlyTheMatchingAccountAndPersistsHash() throws Exception {
        TestContext context = start(dir);
        PassengerAccount passenger = context.accounts.registerPassenger(
                "Reset Passenger", "01712345001", "OldPassenger9!".toCharArray());
        PassengerAccount otherPassenger = context.accounts.registerPassenger(
                "Unchanged Passenger", "01712345002", "OtherPassenger9!".toCharArray());
        String otherPassengerHash = otherPassenger.getPasswordHash();
        String driverCsvBefore = Files.readString(dir.resolve(CsvStorage.DRIVERS_FILE));

        AccountIdentity verified = context.accounts.verifyPasswordResetAccount(
                " reset passenger ", "+880 1712-345001");
        assertEquals(AccountRole.PASSENGER, verified.role());
        assertEquals(passenger.getMobileNumber(), verified.id());

        context.accounts.resetPassword("Reset Passenger", "01712345001", "NewPassenger9!".toCharArray());
        PassengerAccount updated = context.accounts.getPassengerByMobile("01712345001");
        assertEquals(passenger.getFullName(), updated.getFullName());
        assertEquals(passenger.getMobileNumber(), updated.getMobileNumber());
        assertNotEquals(passenger.getPasswordHash(), updated.getPasswordHash());
        assertEquals(otherPassengerHash,
                context.accounts.getPassengerByMobile(otherPassenger.getMobileNumber()).getPasswordHash());
        assertEquals(driverCsvBefore, Files.readString(dir.resolve(CsvStorage.DRIVERS_FILE)));
        assertThrows(IllegalArgumentException.class, () -> context.accounts.authenticatePassenger(
                "01712345001", "OldPassenger9!".toCharArray()));
        assertEquals("01712345001", context.accounts.authenticatePassenger(
                "01712345001", "NewPassenger9!".toCharArray()).getMobileNumber());

        String passengerCsv = Files.readString(dir.resolve(CsvStorage.PASSENGERS_FILE));
        assertTrue(passengerCsv.contains("Reset Passenger,01712345001,PBKDF2WithHmacSHA256"));
        assertTrue(!passengerCsv.contains("NewPassenger9!"));
        TestContext restarted = start(dir);
        assertEquals("01712345001", restarted.accounts.authenticatePassenger(
                "01712345001", "NewPassenger9!".toCharArray()).getMobileNumber());
    }

    @Test
    void driverPasswordResetPreservesDriverAndRickshawInformation() throws Exception {
        TestContext context = start(dir);
        DriverAccount driver = context.accounts.registerDriver(
                "Reset Driver", "01712345003", "OldDriver9!".toCharArray(),
                "RESET-RICK", "bottola", "dairy_gate", 5, 3);
        PassengerAccount passenger = context.accounts.registerPassenger(
                "Unchanged Passenger", "01712345004", "OtherPassenger9!".toCharArray());
        String passengerCsvBefore = Files.readString(dir.resolve(CsvStorage.PASSENGERS_FILE));

        AccountIdentity verified = context.accounts.verifyPasswordResetAccount(
                "Reset Driver", "0171 234 5003");
        assertEquals(AccountRole.DRIVER, verified.role());
        assertEquals("01712345003", verified.id());

        context.accounts.resetPassword("Reset Driver", "01712345003", "NewDriver9!".toCharArray());
        DriverAccount updated = context.accounts.getDriverByMobile("01712345003");
        assertEquals(driver.getFullName(), updated.getFullName());
        assertEquals(driver.getMobileNumber(), updated.getMobileNumber());
        assertEquals(driver.getRickshawId(), updated.getRickshawId());
        assertEquals(driver.getInitialLocationId(), updated.getInitialLocationId());
        assertEquals(driver.getDestinationId(), updated.getDestinationId());
        assertEquals(driver.getTotalSeats(), updated.getTotalSeats());
        assertEquals(driver.getAvailableSeats(), updated.getAvailableSeats());
        assertEquals(driver.getRickshawId(), context.rides.getRickshaw(updated.getRickshawId()).getId());
        assertEquals(passengerCsvBefore, Files.readString(dir.resolve(CsvStorage.PASSENGERS_FILE)));
        assertThrows(IllegalArgumentException.class, () -> context.accounts.authenticateDriver(
                "01712345003", "OldDriver9!".toCharArray()));
        assertEquals(driver.getRickshawId(), context.accounts.authenticateDriver(
                "01712345003", "NewDriver9!".toCharArray()).getRickshawId());

        String driverCsv = Files.readString(dir.resolve(CsvStorage.DRIVERS_FILE));
        assertTrue(driverCsv.contains("Reset Driver,01712345003,PBKDF2WithHmacSHA256$"));
        assertTrue(driverCsv.contains(",RESET-RICK,"));
        assertTrue(!driverCsv.contains("NewDriver9!"));
        TestContext restarted = start(dir);
        assertEquals("RESET-RICK", restarted.accounts.authenticateDriver(
                "01712345003", "NewDriver9!".toCharArray()).getRickshawId());
    }

    @Test
    void passwordResetRejectsIncorrectNameOrIdentifierWithoutChangingAccounts() throws Exception {
        TestContext context = start(dir);
        context.accounts.registerPassenger(
                "Correct Passenger", "01712345005", "OldPassenger9!".toCharArray());
        context.accounts.registerDriver("Correct Driver", "01712345006", "OldDriver9!".toCharArray(),
                "MATCH-RICK", "bottola", "dairy_gate", 4, 4);
        String passengersBefore = Files.readString(dir.resolve(CsvStorage.PASSENGERS_FILE));
        String driversBefore = Files.readString(dir.resolve(CsvStorage.DRIVERS_FILE));

        assertThrows(IllegalArgumentException.class, () ->
                context.accounts.verifyPasswordResetAccount("Wrong Name", "01712345005"));
        assertThrows(IllegalArgumentException.class, () ->
                context.accounts.verifyPasswordResetAccount("Correct Passenger", "01712345006"));
        assertThrows(IllegalArgumentException.class, () ->
                context.accounts.resetPassword("Wrong Name", "01712345005", "NewPassword9!".toCharArray()));
        assertEquals(passengersBefore, Files.readString(dir.resolve(CsvStorage.PASSENGERS_FILE)));
        assertEquals(driversBefore, Files.readString(dir.resolve(CsvStorage.DRIVERS_FILE)));
    }

    @Test
    void passwordResetRequiresAStrongNonEmptyPassword() throws Exception {
        TestContext context = start(dir);
        context.accounts.registerPassenger(
                "Reset Passenger", "01712345007", "OldPassenger9!".toCharArray());

        assertThrows(IllegalArgumentException.class, () -> context.accounts.resetPassword(
                "Reset Passenger", "01712345007", new char[0]));
        assertThrows(IllegalArgumentException.class, () -> context.accounts.resetPassword(
                "Reset Passenger", "01712345007", "weakpass".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> context.accounts.authenticatePassenger(
                "01712345007", "weakpass".toCharArray()));
    }

    @Test
    void localAccountIdCanBeUsedForPasswordRecovery() throws Exception {
        TestContext context = start(dir);
        AccountIdentity identity = context.accounts.createSimpleAccount(
                AccountRole.PASSENGER, "Local Reset Passenger", "old-password".toCharArray());

        assertEquals(identity, context.accounts.verifyPasswordResetAccount(
                "Local Reset Passenger", identity.id().toLowerCase(java.util.Locale.ROOT)));
        context.accounts.resetPassword("Local Reset Passenger",
                identity.id().toLowerCase(java.util.Locale.ROOT), "NewLocalPass9!".toCharArray());

        assertEquals(identity, context.accounts.authenticateAccount(identity, "NewLocalPass9!".toCharArray()));
        assertThrows(IllegalArgumentException.class, () ->
                context.accounts.authenticateAccount(identity, "old-password".toCharArray()));
    }

    @Test
    void tripMovementIntervalIsSlowerThanPreviousSubSecondSetting() {
        assertEquals(2400, RideService.TRIP_STEP_INTERVAL_MS);
    }

    private static TestContext start(Path dataDir) throws Exception {
        CsvStorage storage = new CsvStorage(dataDir);
        RouteService routes = new RouteService(storage.loadLocations(), storage.loadRoads(), storage.getWarnings());
        RideService rides = new RideService(storage, routes);
        AccountService accounts = new AccountService(storage, rides);
        return new TestContext(rides, accounts);
    }

    private record TestContext(RideService rides, AccountService accounts) {
    }
}
