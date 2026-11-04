package ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import service.RideService;
import service.TestApp;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampusMapPanelTest {
    @TempDir
    Path directory;

    @Test
    void liveCampusViewExposesCurrentNodeAndRideStatus() throws Exception {
        RideService service = TestApp.start(directory);
        List<String> route = service.getRouteService()
                .findRoute("bottola", "dairy_gate").getPath();

        SwingUtilities.invokeAndWait(() -> {
            CampusMapPanel panel = new CampusMapPanel(service.getRouteService());
            panel.setTripView(route, route.get(0), "bottola", "dairy_gate", "Ride accepted");

            assertEquals(route.get(0), panel.getCurrentLocationId());
            assertTrue(panel.getTripSummary().contains(
                    service.getRouteService().getLocationName(route.get(0))));
            assertTrue(panel.getTripSummary().contains("Ride accepted"));
        });
    }

    @Test
    void rickshawMarkerIsPaintedAndMovesSmoothlyBetweenGraphNodes() throws Exception {
        RideService service = TestApp.start(directory);
        List<String> route = service.getRouteService()
                .findRoute("dairy_gate", "rafiq_jabbar_hall").getPath();
        long segmentStartedAt = System.currentTimeMillis();
        RideService.TripMovement movement = new RideService.TripMovement(
                route.get(0), route.get(1), segmentStartedAt);
        CampusMapPanel[] panelRef = new CampusMapPanel[1];
        BufferedImage[] before = new BufferedImage[1];
        Point[] start = new Point[1];

        SwingUtilities.invokeAndWait(() -> {
            CampusMapPanel panel = new CampusMapPanel(service.getRouteService());
            panelRef[0] = panel;
            panel.setSize(820, 420);
            panel.setTripView(route, route.get(0), "shaheed_minar", "rafiq_jabbar_hall",
                    "Rickshaw approaching");
            start[0] = panel.getRickshawPosition();
            before[0] = render(panel);
            assertTrue(countRickshawGreenPixels(before[0], start[0]) > 25,
                    "The rendered map should contain the custom green rickshaw icon around its marker.");

            panel.setTripView(route, route.get(1), "shaheed_minar", "rafiq_jabbar_hall",
                    "Rickshaw approaching", movement);
            assertEquals(route.get(1), panel.getCurrentLocationId());
        });

        Thread.sleep(240);
        Point[] moving = new Point[1];
        BufferedImage[] after = new BufferedImage[1];
        float[] progress = new float[1];
        SwingUtilities.invokeAndWait(() -> {
            CampusMapPanel panel = panelRef[0];
            panel.setTripView(route, route.get(1), "shaheed_minar", "rafiq_jabbar_hall",
                    "Rickshaw approaching", movement);
            moving[0] = panel.getRickshawPosition();
            progress[0] = panel.getMovementProgress();
            after[0] = render(panel);
        });

        assertTrue(start[0].distance(moving[0]) > 2.0,
                "The rickshaw icon must be visibly interpolating, not snapped to the next node.");
        assertTrue(progress[0] > 0f && progress[0] < 1f,
                "Refreshing the same service segment must not restart its animation.");
        assertTrue(countImageDifferences(before[0], after[0]) > 100,
                "The map rendering should visibly change as the marker animates.");
        SwingUtilities.invokeAndWait(panelRef[0]::removeNotify);
    }

    @Test
    void mapDoesNotAnimateWhenThereIsNoServiceMovement() throws Exception {
        RideService service = TestApp.start(directory);
        List<String> route = service.getRouteService()
                .findRoute("dairy_gate", "rafiq_jabbar_hall").getPath();

        SwingUtilities.invokeAndWait(() -> {
            CampusMapPanel panel = new CampusMapPanel(service.getRouteService());
            panel.setSize(820, 420);
            panel.setTripView(route, route.get(0), "", "", "Trip not started", null);
            panel.setTripView(route, route.get(1), "", "", "Trip paused", null);

            assertEquals(route.get(1), panel.getCurrentLocationId());
            assertEquals(1f, panel.getMovementProgress());
            assertEquals(service.getRouteService().getLocationName(route.get(1)),
                    service.getRouteService().getLocationName(panel.getCurrentLocationId()));
            panel.removeNotify();
        });
    }

    private static BufferedImage render(CampusMapPanel panel) {
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
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

    private static int countImageDifferences(BufferedImage first, BufferedImage second) {
        int differences = 0;
        for (int x = 0; x < first.getWidth(); x++) {
            for (int y = 0; y < first.getHeight(); y++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    differences++;
                }
            }
        }
        return differences;
    }
}
