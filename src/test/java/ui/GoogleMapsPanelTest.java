package ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import service.RouteService;
import storage.CsvStorage;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleMapsPanelTest {
    @TempDir
    Path directory;

    @Test
    void missingApiKeyShowsConfigurationMessageInsteadOfFakeMap() throws Exception {
        CsvStorage storage = new CsvStorage(directory);
        RouteService routeService = new RouteService(
                storage.loadLocations(), storage.loadRoads(), storage.getWarnings());

        SwingUtilities.invokeAndWait(() -> {
            GoogleMapsPanel panel = new GoogleMapsPanel(routeService, "");

            assertEquals(1, panel.getComponentCount());
            assertTrue(panel.getComponent(0) instanceof JLabel);
            assertTrue(((JLabel) panel.getComponent(0)).getText().contains("GOOGLE_MAPS_API_KEY"));
        });
    }
}
