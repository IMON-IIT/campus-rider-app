package ui;

import dsa.Edge;
import model.Location;
import service.RideService;
import service.RouteService;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A schematic graph view; node positions are for display only, not GPS coordinates. */
public class CampusMapPanel extends JPanel {
    private static final Color ROAD = new Color(211, 218, 226);
    private static final Color ROUTE = new Color(35, 122, 94);
    private static final int ANIMATION_FRAME_MS = 16;

    private final RouteService routeService;
    private final JLabel tripSummary = new JLabel("Rickshaw location: Not assigned");
    private final Timer animationTimer;
    private List<String> route = List.of();
    private String currentLocationId = "";
    private String animationFromId = "";
    private String animationTargetId = "";
    private String pickupId = "";
    private String destinationId = "";
    private String status = "Trip status unavailable";
    private RideService.TripMovement movement;
    private List<String> availableVehicleLocationIds = List.of();

    public CampusMapPanel(RouteService routeService) {
        super(new BorderLayout(0, 4));
        this.routeService = routeService;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(620, 250));
        setMinimumSize(new Dimension(320, 190));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 226, 233)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        setToolTipText("Schematic campus road graph; locations are not plotted using real-world coordinates.");
        tripSummary.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        tripSummary.setForeground(new Color(28, 48, 74));
        tripSummary.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(tripSummary, BorderLayout.NORTH);
        animationTimer = new Timer(ANIMATION_FRAME_MS, event -> {
            if (movementProgress() >= 1f) {
                ((Timer) event.getSource()).stop();
            }
            repaint();
        });
        animationTimer.setCoalesce(true);
    }

    public void setTripView(List<String> route, String currentLocationId,
                            String pickupId, String destinationId) {
        setTripView(route, currentLocationId, pickupId, destinationId,
                "Trip status unavailable", null);
    }

    public void setTripView(List<String> route, String currentLocationId,
                            String pickupId, String destinationId, String status) {
        setTripView(route, currentLocationId, pickupId, destinationId, status, null);
    }

    public void setTripView(List<String> route, String currentLocationId,
                            String pickupId, String destinationId, String status,
                            RideService.TripMovement movement) {
        List<String> routeSnapshot = route == null ? List.of() : List.copyOf(route);
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() ->
                    setTripView(routeSnapshot, currentLocationId, pickupId, destinationId, status, movement));
            return;
        }
        String nextCurrentLocation = currentLocationId == null ? "" : currentLocationId;
        boolean validMovement = movement != null
                && movement.toLocationId().equals(nextCurrentLocation)
                && routeService.isValidLocation(movement.fromLocationId());
        if (!validMovement) {
            this.movement = null;
            animationFromId = "";
            animationTargetId = "";
            animationTimer.stop();
        } else if (!movement.equals(this.movement)) {
            this.movement = movement;
            animationFromId = movement.fromLocationId();
            animationTargetId = movement.toLocationId();
            if (movementProgress() < 1f) {
                animationTimer.start();
            } else {
                animationTimer.stop();
            }
        } else if (movementProgress() < 1f && !animationTimer.isRunning()) {
            animationTimer.start();
        }
        this.route = routeSnapshot;
        this.currentLocationId = nextCurrentLocation;
        this.pickupId = pickupId == null ? "" : pickupId;
        this.destinationId = destinationId == null ? "" : destinationId;
        this.status = status == null || status.isBlank() ? "Trip status unavailable" : status;
        updateTripSummary();
        repaint();
    }

    public String getCurrentLocationId() {
        return currentLocationId;
    }

    public String getTripSummary() {
        return tripSummary.getText();
    }

    public void setAvailableVehicleLocationIds(List<String> locationIds) {
        List<String> snapshot = locationIds == null ? List.of() : List.copyOf(locationIds);
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setAvailableVehicleLocationIds(snapshot));
            return;
        }
        availableVehicleLocationIds = snapshot.stream()
                .filter(routeService::isValidLocation)
                .toList();
        repaint();
    }

    /** Returns the animated marker position in component coordinates. */
    public Point getRickshawPosition() {
        Map<String, Point> points = layout(routeService.getLocationsSorted());
        return currentVehiclePosition(points);
    }

    private void updateTripSummary() {
        String summary;
        if (currentLocationId.isBlank()) {
            summary = "Rickshaw location: Not assigned  |  " + status;
        } else {
            summary = "Rickshaw at: " + routeService.getLocationName(currentLocationId)
                    + "  |  " + status;
        }
        if (!summary.equals(tripSummary.getText())) {
            tripSummary.setText(summary);
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            List<Location> locations = routeService.getLocationsSorted();
            Map<String, Point> points = layout(locations);
            drawRoads(g, locations, points);
            drawRoute(g, points);
            drawLocations(g, locations, points);
            drawStopMarkers(g, points);
            drawAvailableVehicles(g, points);
            drawRickshaw(g, points);
            drawLegend(g);
        } finally {
            g.dispose();
        }
    }

    private Map<String, Point> layout(List<Location> locations) {
        Map<String, Point> points = new HashMap<>();
        if (locations.isEmpty()) {
            return points;
        }
        int centerX = getWidth() / 2;
        int mapTop = Math.min(tripSummary.getHeight() + 8, Math.max(8, getHeight() / 2));
        int mapBottom = Math.max(mapTop, getHeight() - 22);
        int mapHeight = mapBottom - mapTop;
        int centerY = mapTop + mapHeight / 2;
        double radiusX = Math.max(0, getWidth() / 2.0 - 70);
        double radiusY = Math.max(0, mapHeight / 2.0 - 20);
        for (int i = 0; i < locations.size(); i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / locations.size();
            points.put(locations.get(i).getId(), new Point(
                    centerX + (int) (Math.cos(angle) * radiusX),
                    centerY + (int) (Math.sin(angle) * radiusY)));
        }
        return points;
    }

    private void drawRoads(Graphics2D g, List<Location> locations, Map<String, Point> points) {
        g.setColor(ROAD);
        g.setStroke(new BasicStroke(1f));
        for (Location location : locations) {
            Point from = points.get(location.getId());
            for (Edge edge : routeService.getGraph().getNeighbors(location.getId())) {
                if (location.getId().compareTo(edge.getToId()) < 0) {
                    Point to = points.get(edge.getToId());
                    if (to != null) {
                        g.drawLine(from.x, from.y, to.x, to.y);
                    }
                }
            }
        }
    }

    private void drawRoute(Graphics2D g, Map<String, Point> points) {
        g.setColor(new Color(31, 119, 89, 205));
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (isMovementAnimating()) {
            Point from = points.get(animationFromId);
            Point to = points.get(animationTargetId);
            if (from != null && to != null) {
                g.drawLine(from.x, from.y, to.x, to.y);
            }
        }
        for (int i = 1; i < route.size(); i++) {
            Point from = points.get(route.get(i - 1));
            Point to = points.get(route.get(i));
            if (from != null && to != null) {
                g.drawLine(from.x, from.y, to.x, to.y);
            }
        }
    }

    private void drawLocations(Graphics2D g, List<Location> locations, Map<String, Point> points) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        for (Location location : locations) {
            Point point = points.get(location.getId());
            if (point == null || location.getId().equals(pickupId)
                    || location.getId().equals(destinationId)) {
                continue;
            }
            g.setColor(location.getId().equals(currentLocationId) ? new Color(50, 150, 100) : Color.WHITE);
            g.fillOval(point.x - 4, point.y - 4, 8, 8);
            g.setColor(new Color(55, 67, 82));
            g.drawOval(point.x - 4, point.y - 4, 8, 8);
            g.drawString(shortName(location.getName()), point.x + 7, point.y - 5);
        }
    }

    private void drawStopMarkers(Graphics2D g, Map<String, Point> points) {
        drawStopMarker(g, points.get(pickupId), "P", new Color(235, 153, 43));
        drawStopMarker(g, points.get(destinationId), "D", new Color(193, 65, 69));
    }

    private void drawStopMarker(Graphics2D g, Point point, String marker, Color color) {
        if (point == null) {
            return;
        }
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 55));
        g.fill(new Ellipse2D.Double(point.x - 12, point.y - 12, 24, 24));
        g.setColor(Color.WHITE);
        g.fillOval(point.x - 8, point.y - 8, 16, 16);
        g.setColor(color);
        g.setStroke(new BasicStroke(2.5f));
        g.drawOval(point.x - 8, point.y - 8, 16, 16);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        int width = g.getFontMetrics().stringWidth(marker);
        g.drawString(marker, point.x - width / 2, point.y + 4);
    }

    private void drawRickshaw(Graphics2D g, Map<String, Point> points) {
        Point marker = currentVehiclePosition(points);
        if (marker == null) {
            return;
        }
        Point from = isMovementAnimating() ? points.get(animationFromId) : null;
        Point to = isMovementAnimating() ? points.get(animationTargetId) : null;
        double heading = from != null && to != null
                ? Math.atan2(to.y - from.y, to.x - from.x) : routeHeading(points);

        g.setColor(new Color(31, 119, 89, 50));
        g.fillOval(marker.x - 22, marker.y - 22, 44, 44);
        g.setColor(Color.WHITE);
        g.fillOval(marker.x - 18, marker.y - 18, 36, 36);
        g.setColor(ROUTE);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(marker.x - 18, marker.y - 18, 36, 36);

        AffineTransform original = g.getTransform();
        g.translate(marker.x, marker.y);
        g.rotate(heading);
        drawRickshawIcon(g);
        g.setTransform(original);
    }

    private void drawAvailableVehicles(Graphics2D g, Map<String, Point> points) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        Map<String, Integer> offsets = new HashMap<>();
        for (String locationId : availableVehicleLocationIds) {
            Point point = points.get(locationId);
            if (point == null) {
                continue;
            }
            int offset = offsets.merge(locationId, 1, Integer::sum) - 1;
            int x = point.x + 10 + offset / 2 * 15;
            int y = point.y - 20 - offset % 2 * 14;
            g.setColor(Color.WHITE);
            g.fillRoundRect(x - 1, y - 1, 21, 18, 7, 7);
            g.setColor(new Color(43, 102, 65));
            g.fillRoundRect(x, y, 19, 16, 6, 6);
            g.setColor(Color.WHITE);
            g.drawString("R", x + 7, y + 11);
        }
    }

    private void drawRickshawIcon(Graphics2D g) {
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(31, 119, 89));
        g.drawLine(-16, 8, -8, 8);
        g.drawLine(-7, 8, -1, 0);
        g.drawLine(-1, 0, 11, 0);
        g.drawLine(11, 0, 16, 8);
        g.drawLine(11, 0, 8, 8);
        g.drawLine(8, 8, 16, 8);
        g.drawLine(-8, 8, -5, 2);

        g.setColor(new Color(28, 48, 74));
        g.fillOval(-20, 5, 9, 9);
        g.fillOval(11, 5, 9, 9);
        g.setColor(Color.WHITE);
        g.fillOval(-17, 8, 3, 3);
        g.fillOval(14, 8, 3, 3);

        g.setColor(new Color(31, 119, 89));
        Path2D canopy = new Path2D.Double();
        canopy.moveTo(-13, -2);
        canopy.quadTo(-10, -11, 1, -11);
        canopy.lineTo(8, -9);
        canopy.lineTo(10, -2);
        canopy.closePath();
        g.fill(canopy);
        g.setColor(new Color(244, 249, 246));
        g.fillRoundRect(-10, -1, 17, 7, 4, 4);
        g.setColor(new Color(31, 119, 89));
        g.drawRoundRect(-10, -1, 17, 7, 4, 4);
        g.drawLine(7, -5, 12, -5);
        g.drawLine(12, -5, 13, 1);
    }

    private double routeHeading(Map<String, Point> points) {
        if (route.size() < 2) {
            return 0;
        }
        Point current = points.get(currentLocationId);
        Point next = points.get(route.get(1));
        if (current == null || next == null) {
            return 0;
        }
        return Math.atan2(next.y - current.y, next.x - current.x);
    }

    private Point currentVehiclePosition(Map<String, Point> points) {
        if (currentLocationId.isBlank()) {
            return null;
        }
        Point destination = points.get(currentLocationId);
        Point source = isMovementAnimating() ? points.get(animationFromId) : null;
        if (destination == null || source == null) {
            return destination;
        }
        float progress = movementProgress();
        float eased = progress * progress * (3 - 2 * progress);
        return new Point(
                Math.round(source.x + (destination.x - source.x) * eased),
                Math.round(source.y + (destination.y - source.y) * eased));
    }

    float getMovementProgress() {
        return movementProgress();
    }

    private boolean isMovementAnimating() {
        return movement != null && !animationFromId.isBlank()
                && !animationTargetId.isBlank() && movementProgress() < 1f;
    }

    private float movementProgress() {
        if (movement == null) {
            return 1f;
        }
        return Math.min(1f, Math.max(0,
                (System.currentTimeMillis() - movement.startedAtMillis())
                        / (float) RideService.TRIP_STEP_INTERVAL_MS));
    }

    private void drawLegend(Graphics2D g) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.setColor(ROUTE);
        g.drawString("Schematic campus graph • not GPS  |  Rickshaw  |  R Available  |  P Pickup  |  D Destination",
                10, getHeight() - 10);
    }

    private String shortName(String name) {
        return name.length() > 19 ? name.substring(0, 17) + "..." : name;
    }

    @Override
    public void removeNotify() {
        animationTimer.stop();
        super.removeNotify();
    }
}
