package ui;

import dsa.QueuePolicy;
import model.DriverAccount;
import model.Location;
import model.PassengerType;
import model.PassengerAccount;
import model.RideRequest;
import model.RideStatus;
import model.Rickshaw;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import service.AccountService;
import service.RideService;
import service.TestApp;
import storage.CsvStorage;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.Timer;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Clicks the real buttons (without showing a window) and checks the services react. */
class PanelTest {

    @TempDir
    Path dir;

    @AfterEach
    void closeSwingWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) {
                if (window.isDisplayable()) {
                    window.dispose();
                }
            }
        });
    }

    private static void select(JComboBox<Location> box, String id) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).getId().equals(id)) {
                box.setSelectedIndex(i);
                return;
            }
        }
        throw new IllegalArgumentException(id);
    }

    private static void selectAccount(LoginWindow login, AccountService accounts,
                                      String id, AccountService.AccountRole role) {
        AccountService.AccountIdentity account = accounts.getAccounts(role).stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        login.selectAccountForLogin(account);
    }

    private static void selectRide(JList<RideRequest> list, int rideId) {
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (list.getModel().getElementAt(i).getId() == rideId) {
                list.setSelectedIndex(i);
                return;
            }
        }
        throw new IllegalArgumentException("Ride #" + rideId + " is not visible in the selected list.");
    }

    private static Point readMapPosition(PassengerDashboard dashboard) throws Exception {
        AtomicReference<Point> position = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> position.set(getField(dashboard,
                "campusMapPanel", CampusMapPanel.class).getRickshawPosition()));
        return position.get();
    }

    private static float mapMovementProgress(Object dashboard) {
        AtomicReference<Float> progress = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> progress.set(getField(dashboard,
                    "campusMapPanel", CampusMapPanel.class).getMovementProgress()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the live map movement progress.", e);
        }
        return progress.get();
    }

    private static boolean isButtonEnabled(Object dashboard, String fieldName) {
        AtomicReference<Boolean> enabled = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> enabled.set(getField(dashboard, fieldName, JButton.class).isEnabled()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not inspect " + fieldName + ".", e);
        }
        return enabled.get();
    }

    private static String passengerMapLocation(PassengerDashboard dashboard) {
        AtomicReference<String> location = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> location.set(getField(dashboard,
                    "campusMapPanel", CampusMapPanel.class).getCurrentLocationId()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return location.get();
    }

    private static String passengerStatus(PassengerDashboard dashboard) {
        AtomicReference<String> status = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> status.set(getField(dashboard,
                    "requestStatus", javax.swing.JLabel.class).getText()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return status.get();
    }

    private static BufferedImage renderPassengerMap(PassengerDashboard dashboard) throws Exception {
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            CampusMapPanel panel = getField(dashboard, "campusMapPanel", CampusMapPanel.class);
            BufferedImage rendered = new BufferedImage(panel.getWidth(), panel.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = rendered.createGraphics();
            try {
                panel.paint(graphics);
            } finally {
                graphics.dispose();
            }
            image.set(rendered);
        });
        return image.get();
    }

    private static int countRickshawGreenPixels(BufferedImage image, Point center) {
        int count = 0;
        for (int x = Math.max(0, center.x - 20); x < Math.min(image.getWidth(), center.x + 21); x++) {
            for (int y = Math.max(0, center.y - 20); y < Math.min(image.getHeight(), center.y + 21); y++) {
                Color pixel = new Color(image.getRGB(x, y), true);
                if (pixel.getGreen() > 80 && pixel.getGreen() > pixel.getRed() * 1.15
                        && pixel.getGreen() > pixel.getBlue() * 0.9) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void passengerRequestButtonDependsOnActiveRequestAndStatusesAreClear() {
        assertTrue(PassengerDashboard.canSubmitRide(false, false, true));
        assertFalse(PassengerDashboard.canSubmitRide(true, false, true));
        assertFalse(PassengerDashboard.canSubmitRide(false, true, true));
        assertFalse(PassengerDashboard.canSubmitRide(false, false, false));

        RideRequest pending = new RideRequest(1, "Passenger", PassengerType.STUDENT,
                "bottola", "dairy_gate", 100, RideStatus.OFFERED, "");
        assertTrue(PassengerDashboard.requestStatusText(pending).contains("Pending"));

        RideRequest rejected = new RideRequest(2, "Passenger", PassengerType.STUDENT,
                "bottola", "dairy_gate", 100, RideStatus.OFFERED, "RICK-2", java.util.List.of("RICK-1"));
        assertTrue(PassengerDashboard.requestStatusText(rejected).contains("Rejected"));

        RideRequest accepted = new RideRequest(3, "Passenger", PassengerType.STUDENT,
                "bottola", "dairy_gate", 100, RideStatus.MATCHED, "RICK-2");
        assertTrue(PassengerDashboard.requestStatusText(accepted).contains("Accepted"));
        accepted.moveTo(RideStatus.ARRIVED_AT_PICKUP);
        assertTrue(PassengerDashboard.requestStatusText(accepted).contains("Accepted"));
        accepted.moveTo(RideStatus.IN_PROGRESS);
        assertTrue(PassengerDashboard.requestStatusText(accepted).contains("Passenger Onboard"));
        accepted.moveTo(RideStatus.ARRIVED_AT_DESTINATION);
        assertTrue(PassengerDashboard.requestStatusText(accepted).contains("awaiting driver"));
        accepted.moveTo(RideStatus.COMPLETED);
        assertEquals("Trip Completed", PassengerDashboard.requestStatusText(accepted));

        RideRequest unavailable = new RideRequest(4, "Passenger", PassengerType.STUDENT,
                "bottola", "dairy_gate", 100, RideStatus.NO_DRIVER_AVAILABLE, "");
        assertEquals("No Driver Available", PassengerDashboard.requestStatusText(unavailable));
    }

    @Test
    void driverDashboardRefreshRemainsResponsiveWhileSimulationIsSlower() {
        assertEquals(250, DriverDashboard.VIEW_REFRESH_INTERVAL_MS);
        assertTrue(DriverDashboard.VIEW_REFRESH_INTERVAL_MS < RideService.TRIP_STEP_INTERVAL_MS);
    }

    @Test
    void loggedInDashboardsDeliverAcceptAndSimulatePassengerRide() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "The full dashboard workflow requires a graphical desktop.");

        CsvStorage storage = new CsvStorage(dir);
        RideService service = TestApp.start(dir);
        AccountService accounts = new AccountService(storage, service);
        DriverAccount driver = accounts.registerDriver("Workflow Driver", "01700001001",
                "workflow-pass".toCharArray(), "WORKFLOW-RICK", "dairy_gate",
                "rafiq_jabbar_hall", 4, 4);
        PassengerAccount passengerA = accounts.registerPassenger("Passenger A", "01700001002",
                "workflow-pass".toCharArray());
        PassengerAccount passengerB = accounts.registerPassenger("Passenger B", "01700001003",
                "workflow-pass".toCharArray());

        AtomicReference<LoginWindow> additionalLoginA = new AtomicReference<>();
        AtomicReference<LoginWindow> additionalLoginB = new AtomicReference<>();
        AtomicReference<LoginWindow> initialLogin = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            LoginWindow login = new LoginWindow(service, accounts);
            initialLogin.set(login);
            selectAccount(login, accounts, driver.getMobileNumber(), AccountService.AccountRole.DRIVER);
            getField(login, "passwordField", JPasswordField.class).setText("workflow-pass");
            login.setVisible(true);
            findButton(login, "Log In").doClick();
        });
        await(() -> visibleWindow(DriverDashboard.class) != null, 3_000);
        DriverDashboard driverScreen = visibleWindow(DriverDashboard.class);
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(service.getRickshaw(driver.getRickshawId()),
                    getField(driverScreen, "driver", Rickshaw.class));
            assertEquals(accounts.getDriverByMobile(driver.getMobileNumber()),
                    getField(driverScreen, "driverAccount", DriverAccount.class));
            assertTrue(getField(driverScreen, "refreshTimer", Timer.class).isRunning());
            assertTrue(getField(driverScreen, "simulationTimer", Timer.class).isRunning());
        });

        SwingUtilities.invokeAndWait(() -> {
            javax.swing.JToggleButton onlineToggle =
                    getField(driverScreen, "onlineToggle", javax.swing.JToggleButton.class);
            if (onlineToggle.isSelected()) {
                onlineToggle.doClick();
            }
            onlineToggle.doClick();
            findButton(driverScreen, "Start Trip").doClick();
            findButton(driverScreen, "Open Another Session").doClick();
        });
        await(() -> visibleWindow(LoginWindow.class) != null, 3_000);
        LoginWindow passengerLoginA = visibleWindow(LoginWindow.class);
        additionalLoginA.set(passengerLoginA);
        SwingUtilities.invokeAndWait(() -> {
            selectAccount(passengerLoginA, accounts, passengerA.getMobileNumber(),
                    AccountService.AccountRole.PASSENGER);
            getField(passengerLoginA, "passwordField", JPasswordField.class).setText("workflow-pass");
            findButton(passengerLoginA, "Log In").doClick();
        });
        await(() -> visibleWindow(PassengerDashboard.class) != null, 3_000);
        PassengerDashboard passengerScreenA = visibleWindow(PassengerDashboard.class);

        SwingUtilities.invokeAndWait(() -> {
            select(getField(passengerScreenA, "pickupBox", JComboBox.class), "shaheed_minar");
            select(getField(passengerScreenA, "destinationBox", JComboBox.class), "central_library");
            getField(passengerScreenA, "seatsSpinner", JSpinner.class).setValue(1);
            findButton(passengerScreenA, "Search Available Trips").doClick();
        });
        await(() -> listSize(passengerScreenA, "rickshawList") > 0, 3_000);
        Point startMarker = readMapPosition(passengerScreenA);
        SwingUtilities.invokeAndWait(() -> getField(passengerScreenA, "requestRideButton", JButton.class).doClick());
        await(() -> !service.getRides().isEmpty(), 3_000);

        RideRequest requestA = service.getRides().get(0);
        assertEquals(RideStatus.OFFERED, requestA.getStatus());
        assertEquals("WORKFLOW-RICK", requestA.getRickshawId());
        assertEquals(requestA.getId(), storage.loadRides().get(0).getId(),
                "The request must be persisted before the driver responds.");
        await(() -> listSize(driverScreen, "requestsModel") == 1, 3_000);

        SwingUtilities.invokeAndWait(() -> {
            getField(driverScreen, "requestList", JList.class).setSelectedIndex(0);
            getField(driverScreen, "acceptButton", JButton.class).doClick();
        });
        await(() -> requestA.getStatus() == RideStatus.MATCHED, 3_000);
        assertEquals(3, service.getSeatAvailability("WORKFLOW-RICK").availableSeats());
        await(() -> {
            AtomicReference<String> label = new AtomicReference<>();
            try {
                SwingUtilities.invokeAndWait(() -> label.set(getField(passengerScreenA,
                        "requestStatus", javax.swing.JLabel.class).getText()));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return label.get().contains("Request Accepted");
        }, 3_000);
        await(() -> !service.getRickshaw("WORKFLOW-RICK").getLocationId().equals("dairy_gate"), 5_000);
        await(() -> passengerMapLocation(passengerScreenA)
                .equals(service.getRickshaw("WORKFLOW-RICK").getLocationId())
                && mapMovementProgress(passengerScreenA) > 0.1f
                && mapMovementProgress(driverScreen) > 0.1f, 3_000);
        assertTrue(Math.abs(mapMovementProgress(passengerScreenA)
                - mapMovementProgress(driverScreen)) < 0.1f,
                "Driver and passenger maps should interpolate the same service movement segment.");
        Point movingMarker = readMapPosition(passengerScreenA);
        assertTrue(startMarker.distance(movingMarker) > 2.0,
                () -> "The visible map marker should move after the driver accepts the request. Start: "
                        + startMarker + ", current: " + movingMarker + ", node: "
                        + passengerMapLocation(passengerScreenA) + ", map size: "
                        + getField(passengerScreenA, "campusMapPanel", CampusMapPanel.class).getSize());
        BufferedImage mapImage = renderPassengerMap(passengerScreenA);
        int iconPixels = countRickshawGreenPixels(mapImage, movingMarker);
        assertTrue(iconPixels > 25,
                () -> "The displayed dashboard map should paint the custom rickshaw icon. Pixels: "
                        + iconPixels + ", map: " + mapImage.getWidth() + "x" + mapImage.getHeight()
                        + ", marker: " + movingMarker);
        assertTrue(requestA.getStatus() == RideStatus.MATCHED
                        || requestA.getStatus() == RideStatus.ARRIVED_AT_PICKUP,
                "Passenger dashboard should observe the accepted request and the approaching vehicle.");

        await(() -> requestA.getStatus() == RideStatus.ARRIVED_AT_PICKUP, 15_000);
        SwingUtilities.invokeAndWait(() -> {
            selectRide(getField(driverScreen, "currentRideList", JList.class), requestA.getId());
            getField(driverScreen, "confirmPickupButton", JButton.class).doClick();
            findButton(passengerScreenA, "Open Another Session").doClick();
        });
        await(() -> requestA.getStatus() == RideStatus.IN_PROGRESS, 3_000);
        try {
            await(() -> passengerStatus(passengerScreenA).contains("Passenger Onboard"), 3_000);
        } catch (AssertionError error) {
            throw new AssertionError("Passenger dashboard did not refresh onboard status. UI: "
                    + passengerStatus(passengerScreenA) + ", service: " + requestA.getStatus(), error);
        }

        await(() -> visibleWindow(LoginWindow.class) != null, 3_000);
        LoginWindow passengerLoginB = visibleWindow(LoginWindow.class);
        additionalLoginB.set(passengerLoginB);
        SwingUtilities.invokeAndWait(() -> {
            selectAccount(passengerLoginB, accounts, passengerB.getMobileNumber(),
                    AccountService.AccountRole.PASSENGER);
            getField(passengerLoginB, "passwordField", JPasswordField.class).setText("workflow-pass");
            findButton(passengerLoginB, "Log In").doClick();
        });
        await(() -> visibleWindowOther(PassengerDashboard.class, passengerScreenA) != null, 3_000);
        PassengerDashboard passengerScreenB = visibleWindowOther(PassengerDashboard.class, passengerScreenA);
        SwingUtilities.invokeAndWait(() -> {
            select(getField(passengerScreenB, "pickupBox", JComboBox.class), "central_library");
            select(getField(passengerScreenB, "destinationBox", JComboBox.class), "rafiq_jabbar_hall");
            getField(passengerScreenB, "seatsSpinner", JSpinner.class).setValue(2);
            findButton(passengerScreenB, "Search Available Trips").doClick();
        });
        await(() -> listSize(passengerScreenB, "rickshawList") > 0, 4_000);
        SwingUtilities.invokeAndWait(() ->
                getField(passengerScreenB, "requestRideButton", JButton.class).doClick());
        await(() -> service.getRides().size() == 2, 3_000);
        RideRequest requestB = service.getRides().stream()
                .filter(ride -> ride.getPassengerMobileNumber().equals(passengerB.getMobileNumber()))
                .findFirst().orElseThrow();
        await(() -> listSize(driverScreen, "requestsModel") == 1, 3_000);
        SwingUtilities.invokeAndWait(() -> {
            getField(driverScreen, "requestList", JList.class).setSelectedIndex(0);
            getField(driverScreen, "acceptButton", JButton.class).doClick();
        });
        await(() -> requestB.getStatus() == RideStatus.MATCHED, 3_000);
        RideService.SeatAvailability sharedSeats = service.getSeatAvailability("WORKFLOW-RICK");
        assertEquals(3, sharedSeats.bookedSeats());
        assertEquals(1, sharedSeats.availableSeats());
        assertEquals(1, sharedSeats.occupiedSeats());
        await(() -> listSize(driverScreen, "currentRideModel") == 2, 3_000);

        try {
            await(() -> requestA.getStatus() == RideStatus.ARRIVED_AT_DESTINATION, 45_000);
        } catch (AssertionError error) {
            throw new AssertionError("Ride A status=" + requestA.getStatus()
                    + ", location=" + service.getRickshaw("WORKFLOW-RICK").getLocationId()
                    + ", itinerary=" + service.getRickshaw("WORKFLOW-RICK").getItinerary()
                    + ", simulation timer running=" + getField(driverScreen, "simulationTimer", Timer.class)
                    .isRunning(), error);
        }
        SwingUtilities.invokeAndWait(() ->
                selectRide(getField(driverScreen, "currentRideList", JList.class), requestA.getId()));
        await(() -> isButtonEnabled(driverScreen, "completePassengerRideButton"), 3_000);
        SwingUtilities.invokeAndWait(() ->
                getField(driverScreen, "completePassengerRideButton", JButton.class).doClick());
        await(() -> requestA.getStatus() == RideStatus.COMPLETED, 3_000);
        await(() -> requestB.getStatus() == RideStatus.ARRIVED_AT_PICKUP, 30_000);
        SwingUtilities.invokeAndWait(() ->
                selectRide(getField(driverScreen, "currentRideList", JList.class), requestB.getId()));
        await(() -> isButtonEnabled(driverScreen, "confirmPickupButton"), 3_000);
        SwingUtilities.invokeAndWait(() ->
                getField(driverScreen, "confirmPickupButton", JButton.class).doClick());
        await(() -> requestB.getStatus() == RideStatus.IN_PROGRESS, 3_000);
        await(() -> requestB.getStatus() == RideStatus.ARRIVED_AT_DESTINATION, 45_000);
        SwingUtilities.invokeAndWait(() ->
                selectRide(getField(driverScreen, "currentRideList", JList.class), requestB.getId()));
        await(() -> isButtonEnabled(driverScreen, "completePassengerRideButton"), 3_000);
        SwingUtilities.invokeAndWait(() ->
                getField(driverScreen, "completePassengerRideButton", JButton.class).doClick());
        await(() -> requestB.getStatus() == RideStatus.COMPLETED, 3_000);
        await(() -> passengerStatus(passengerScreenA).contains("Trip Completed")
                && passengerStatus(passengerScreenB).contains("Trip Completed"), 3_000);
        assertEquals(4, service.getSeatAvailability("WORKFLOW-RICK").availableSeats());
        assertEquals(RideStatus.COMPLETED, storage.loadRides().stream()
                .filter(ride -> ride.getId() == requestA.getId()).findFirst().orElseThrow().getStatus());
        assertEquals(RideStatus.COMPLETED, storage.loadRides().stream()
                .filter(ride -> ride.getId() == requestB.getId()).findFirst().orElseThrow().getStatus());

        RideService restarted = TestApp.start(dir);
        assertEquals(RideStatus.COMPLETED, restarted.getRide(requestA.getId()).getStatus());
        assertEquals(RideStatus.COMPLETED, restarted.getRide(requestB.getId()).getStatus());

        SwingUtilities.invokeAndWait(() -> {
            passengerScreenA.dispose();
            passengerScreenB.dispose();
            driverScreen.dispose();
            if (additionalLoginA.get().isDisplayable()) {
                additionalLoginA.get().dispose();
            }
            if (additionalLoginB.get().isDisplayable()) {
                additionalLoginB.get().dispose();
            }
            if (initialLogin.get().isDisplayable()) {
                initialLogin.get().dispose();
            }
        });
    }

    @Test
    void routePanelSearchAndFindRoute() throws Exception {
        RideService service = TestApp.start(dir);
        SwingUtilities.invokeAndWait(() -> {
            RoutePanel panel = new RoutePanel(service.getRouteService());

            panel.searchField.setText("gate"); // the document listener runs the trie search
            assertEquals(3, panel.matchesModel.size());
            panel.useAsStartButton.doClick();
            assertEquals("bishmile_gate", ((Location) panel.fromBox.getSelectedItem()).getId());

            panel.searchField.setText("tsc");
            assertEquals(1, panel.matchesModel.size());
            panel.useAsDestinationButton.doClick();
            assertEquals("central_cafeteria", ((Location) panel.toBox.getSelectedItem()).getId());

            panel.findRouteButton.doClick();
            assertFalse(panel.message.isError());
            String expected = service.getRouteService().describeRoute(
                    service.getRouteService().findRoute("bishmile_gate", "central_cafeteria"));
            assertEquals(expected, panel.resultArea.getText());
            assertTrue(panel.resultArea.getText().contains("Total distance"));

            panel.searchField.setText("zzz");
            assertEquals(0, panel.matchesModel.size());
            panel.useAsStartButton.doClick();
            assertTrue(panel.message.isError());
        });
    }

    @Test
    void ridePanelButtonsDriveTheRideLifecycle() throws Exception {
        RideService service = TestApp.start(dir);
        SwingUtilities.invokeAndWait(() -> {
            RidePanel panel = new RidePanel(service);

            // Empty name shows an error and creates nothing.
            panel.createButton.doClick();
            assertTrue(panel.message.isError());
            assertTrue(service.getRides().isEmpty());

            panel.nameField.setText("Rafiq");
            panel.typeBox.setSelectedItem(PassengerType.STAFF);
            select(panel.pickupBox, "bottola");
            select(panel.destinationBox, "dairy_gate");
            panel.createButton.doClick();
            assertFalse(panel.message.isError());
            assertEquals(1, service.getRides().size());
            assertEquals(1, panel.ridesModel.getRowCount());

            panel.matchButton.doClick();
            assertEquals(RideStatus.MATCHED, service.getRide(1).getStatus());
            assertEquals("BUSY", panel.rickshawsModel.getValueAt(1, 3)); // JU-RICK-102

            panel.ridesTable.setRowSelectionInterval(0, 0);
            panel.completeButton.doClick(); // MATCHED -> COMPLETED is not allowed
            assertTrue(panel.message.isError());
            panel.startButton.doClick();
            assertEquals(RideStatus.IN_PROGRESS, service.getRide(1).getStatus());
            panel.completeButton.doClick();
            assertEquals(RideStatus.COMPLETED, service.getRide(1).getStatus());
            assertEquals("COMPLETED", panel.ridesModel.getValueAt(0, 6).toString());

            panel.policyBox.setSelectedItem(QueuePolicy.PRIORITY);
            assertEquals(QueuePolicy.PRIORITY, service.getQueuePolicy());

            panel.ridesTable.clearSelection();
            panel.cancelButton.doClick();
            assertTrue(panel.message.isError()); // no ride selected
        });
    }

    private static <T> T getField(Object target, String name, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read test UI field " + name, e);
        }
    }

    private static JButton findButton(Container container, String text) {
        JButton button = findButtonOrNull(container, text);
        if (button == null) {
            throw new IllegalArgumentException("Button not found: " + text);
        }
        return button;
    }

    private static JButton findButtonOrNull(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (component instanceof Container child) {
                JButton found = findButtonOrNull(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T extends Window> T visibleWindow(Class<T> type) {
        for (Window window : Window.getWindows()) {
            if (type.isInstance(window) && window.isShowing()) {
                return type.cast(window);
            }
        }
        return null;
    }

    private static <T extends Window> T visibleWindowOther(Class<T> type, Window excluded) {
        for (Window window : Window.getWindows()) {
            if (type.isInstance(window) && window.isShowing() && window != excluded) {
                return type.cast(window);
            }
        }
        return null;
    }

    private static int listSize(Object dashboard, String fieldName) {
        AtomicInteger size = new AtomicInteger(-1);
        try {
            SwingUtilities.invokeAndWait(() -> {
                Object value = getField(dashboard, fieldName, Object.class);
                size.set(value instanceof JList<?> list ? list.getModel().getSize()
                        : ((DefaultListModel<?>) value).getSize());
            });
        } catch (Exception e) {
            throw new IllegalStateException("Could not inspect list " + fieldName, e);
        }
        return size.get();
    }

    private static void await(BooleanSupplier condition, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(80);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for the Swing workflow.");
    }
}
