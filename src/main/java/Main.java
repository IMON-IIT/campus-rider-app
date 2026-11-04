import service.RideService;
import service.RouteService;
import service.AccountService;
import storage.CsvStorage;
import ui.LoginWindow;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Entry point. Loads the CSV files, builds the services and opens the window.
 *
 * Data folder: project "data" folder when launched from the project, or the
 * folder given with -Dcampusride.dataDir=/some/folder.
 */
public class Main {

    public static void main(String[] args) {
        Path dataDir = resolveDataDirectory();
        System.out.println("Data folder: " + dataDir);

        RideService rideService;
        AccountService accountService;
        CsvStorage storage = new CsvStorage(dataDir);
        try {
            RouteService routeService = new RouteService(storage.loadLocations(), storage.loadRoads(),
                    storage.getWarnings());
            rideService = new RideService(storage, routeService);
            accountService = new AccountService(storage, rideService);
            System.out.println("Loaded " + routeService.getGraph().getLocationCount() + " locations, "
                    + routeService.getGraph().getRoadCount() + " roads, "
                    + rideService.getRickshaws().size() + " rickshaws, "
                    + rideService.getRides().size() + " rides.");
        } catch (Exception e) {
            System.err.println("Could not load data: " + e.getMessage());
            showErrorAndExit("Could not load the data files from\n" + dataDir + "\n\n" + e.getMessage());
            return;
        }

        List<String> warnings = storage.getWarnings();
        for (String warning : warnings) {
            System.out.println("Warning: " + warning);
        }

        // Swing components must be created and used on the Event Dispatch Thread.
        SwingUtilities.invokeLater(() -> {
            LoginWindow window = new LoginWindow(rideService, accountService);
            window.setVisible(true);
            if (!warnings.isEmpty()) {
                JOptionPane.showMessageDialog(window, String.join("\n", warnings),
                        "Data file notes", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    static Path resolveDataDirectory() {
        return resolveDataDirectory(System.getProperty("campusride.dataDir"),
                Paths.get("").toAbsolutePath(), getApplicationCodeLocation());
    }

    static Path resolveDataDirectory(String configuredDirectory, Path workingDirectory, Path codeLocation) {
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            return Paths.get(configuredDirectory).toAbsolutePath().normalize();
        }

        Path applicationDirectory = Files.isDirectory(codeLocation)
                ? codeLocation : codeLocation.getParent();
        for (Path candidate = applicationDirectory; candidate != null; candidate = candidate.getParent()) {
            Path projectData = candidate.resolve("data");
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isRegularFile(projectData.resolve(CsvStorage.LOCATIONS_FILE))) {
                return projectData.toAbsolutePath().normalize();
            }
        }
        if (applicationDirectory != null
                && Files.isRegularFile(applicationDirectory.resolve("data")
                        .resolve(CsvStorage.LOCATIONS_FILE))) {
            return applicationDirectory.resolve("data").toAbsolutePath().normalize();
        }
        return workingDirectory.resolve("data").toAbsolutePath().normalize();
    }

    private static Path getApplicationCodeLocation() {
        try {
            return Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Could not determine the application's location.", e);
        }
    }

    private static void showErrorAndExit(String text) {
        try {
            SwingUtilities.invokeAndWait(() ->
                    JOptionPane.showMessageDialog(null, text, "Startup error", JOptionPane.ERROR_MESSAGE));
        } catch (Exception ignored) {
            // no display available; the error was already printed
        }
        System.exit(1);
    }
}
