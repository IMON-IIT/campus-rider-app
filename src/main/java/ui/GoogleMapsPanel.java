package ui;

import model.Location;
import service.RouteService;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.concurrent.Worker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Google Maps visualization embedded in Swing through JavaFX WebView. */
public class GoogleMapsPanel extends JPanel {
    private static final String API_KEY_ENV = "GOOGLE_MAPS_API_KEY";

    private final RouteService routeService;
    private final String apiKey;
    private final JLabel configurationMessage = new JLabel("", SwingConstants.CENTER);
    private JFXPanel browserPanel;
    private volatile WebEngine webEngine;
    private volatile TripView currentTripView = new TripView(List.of(), "", "", "");
    private volatile boolean pageReady;

    public GoogleMapsPanel(RouteService routeService) {
        this(routeService, System.getenv(API_KEY_ENV));
    }

    public static boolean isConfigured() {
        String apiKey = System.getenv(API_KEY_ENV);
        return apiKey != null && !apiKey.isBlank();
    }

    GoogleMapsPanel(RouteService routeService, String apiKey) {
        super(new BorderLayout());
        this.routeService = routeService;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(620, 250));
        setMinimumSize(new Dimension(320, 190));
        setBorder(BorderFactory.createLineBorder(new Color(220, 226, 233)));
        if (this.apiKey.isEmpty()) {
            showConfigurationMessage("Google Maps is not configured. Set GOOGLE_MAPS_API_KEY to enable the live map.");
            return;
        }
        initializeBrowser();
    }

    public void setTripView(List<String> route, String currentLocationId,
                            String pickupId, String destinationId) {
        TripView next = new TripView(List.copyOf(route),
                safeId(currentLocationId), safeId(pickupId), safeId(destinationId));
        if (next.equals(currentTripView)) {
            return;
        }
        currentTripView = next;
        if (pageReady) {
            renderTrip(next);
        }
    }

    private void initializeBrowser() {
        browserPanel = new JFXPanel();
        add(browserPanel, BorderLayout.CENTER);
        Platform.runLater(() -> {
            WebView webView = new WebView();
            webEngine = webView.getEngine();
            webEngine.getLoadWorker().stateProperty().addListener((observable, previous, state) -> {
                if (state == Worker.State.SUCCEEDED) {
                    pageReady = true;
                    renderTrip(currentTripView);
                } else if (state == Worker.State.FAILED) {
                    showConfigurationMessage("Google Maps could not be loaded. Check network access and API configuration.");
                }
            });
            webEngine.loadContent(createMapPage(apiKey));
            browserPanel.setScene(new Scene(webView));
        });
    }

    private void renderTrip(TripView trip) {
        if (webEngine == null || !pageReady) {
            return;
        }
        String data = toJson(trip);
        Platform.runLater(() -> {
            try {
                webEngine.executeScript("window.updateTrip(" + data + ")");
            } catch (RuntimeException e) {
                showConfigurationMessage("Google Maps is still loading. Check the API key and Maps JavaScript API access.");
            }
        });
    }

    private String toJson(TripView trip) {
        StringBuilder json = new StringBuilder("{\"route\":[");
        for (int i = 0; i < trip.route().size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(quote(trip.route().get(i)));
        }
        json.append("],\"current\":").append(quote(trip.currentLocationId()))
                .append(",\"pickup\":").append(quote(trip.pickupId()))
                .append(",\"destination\":").append(quote(trip.destinationId()))
                .append(",\"locations\":{");
        Set<String> ids = new LinkedHashSet<>(trip.route());
        addIfPresent(ids, trip.currentLocationId());
        addIfPresent(ids, trip.pickupId());
        addIfPresent(ids, trip.destinationId());
        int index = 0;
        for (String id : ids) {
            Location location = routeService.getGraph().getLocation(id);
            if (location == null) {
                continue;
            }
            if (index++ > 0) {
                json.append(',');
            }
            json.append(quote(id)).append(':').append(quote(location.getName()));
        }
        return json.append("}}").toString();
    }

    private void showConfigurationMessage(String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showConfigurationMessage(text));
            return;
        }
        configurationMessage.setText("<html><div style='text-align:center;width:320px'>"
                + text + "</div></html>");
        configurationMessage.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        configurationMessage.setForeground(new Color(96, 111, 130));
        if (browserPanel != null) {
            remove(browserPanel);
            browserPanel = null;
        }
        if (configurationMessage.getParent() != this) {
            add(configurationMessage, BorderLayout.CENTER);
        }
        revalidate();
        repaint();
    }

    private static String createMapPage(String apiKey) {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <style>
                    html, body, #map { height: 100%; margin: 0; }
                    #status {
                      position: absolute; z-index: 1; left: 10px; top: 10px;
                      padding: 8px 10px; max-width: 80%; background: white;
                      color: #435166; font: 13px sans-serif; border-radius: 4px;
                    }
                  </style>
                </head>
                <body>
                  <div id="status">Loading Google Maps…</div>
                  <div id="map"></div>
                  <script>
                    let map;
                    let geocoder;
                    let routeLine;
                    let markers = [];
                    let latestTrip = null;
                    const coordinateCache = new Map();
                    const statusBox = document.getElementById("status");
                    const campusAddress = "Jahangirnagar University, Savar, Bangladesh";

                    async function geocode(address) {
                      if (!coordinateCache.has(address)) {
                        coordinateCache.set(address, geocoder.geocode({ address })
                          .then(({ results }) => {
                            if (!results || results.length === 0) {
                              throw new Error("No map coordinates found for " + address);
                            }
                            return results[0].geometry.location;
                          }));
                      }
                      return coordinateCache.get(address);
                    }

                    async function initializeMap() {
                      try {
                        geocoder = new google.maps.Geocoder();
                        const center = await geocode(campusAddress);
                        map = new google.maps.Map(document.getElementById("map"), {
                          center, zoom: 15, mapTypeControl: false, streetViewControl: false
                        });
                        statusBox.textContent = "Google Maps · campus locations are geocoded by place name";
                        if (latestTrip) {
                          await updateTrip(latestTrip);
                        }
                      } catch (error) {
                        statusBox.textContent = "Google Maps setup failed: " + error.message;
                      }
                    }

                    async function updateTrip(trip) {
                      latestTrip = trip;
                      if (!map || !geocoder) return;
                      const allIds = [...new Set([
                        ...(trip.route || []), trip.current, trip.pickup, trip.destination
                      ].filter(Boolean))];
                      try {
                        const coordinates = await Promise.all(allIds.map(async id => {
                          const name = trip.locations[id];
                          if (!name) throw new Error("Campus location is missing: " + id);
                          return [id, await geocode(name + ", " + campusAddress)];
                        }));
                        const points = new Map(coordinates);
                        markers.forEach(marker => marker.setMap(null));
                        markers = [];
                        if (routeLine) routeLine.setMap(null);
                        const path = (trip.route || []).map(id => points.get(id)).filter(Boolean);
                        if (path.length > 1) {
                          routeLine = new google.maps.Polyline({
                            path, geodesic: true, strokeColor: "#1f7759",
                            strokeOpacity: 0.9, strokeWeight: 5, map
                          });
                        }
                        [
                          [trip.current, "R", "#328f69"],
                          [trip.pickup, "P", "#e0a13b"],
                          [trip.destination, "D", "#c54343"]
                        ].forEach(([id, label, color]) => {
                          if (id && points.has(id)) {
                            markers.push(new google.maps.Marker({
                              map, position: points.get(id), label,
                              title: trip.locations[id], icon: {
                                path: google.maps.SymbolPath.CIRCLE, scale: 8,
                                fillColor: color, fillOpacity: 1,
                                strokeColor: "#ffffff", strokeWeight: 2
                              }
                            }));
                          }
                        });
                        const bounds = new google.maps.LatLngBounds();
                        points.forEach(point => bounds.extend(point));
                        if (!bounds.isEmpty()) map.fitBounds(bounds);
                        statusBox.textContent = "Google Maps · rickshaw, pickup, destination and Dijkstra route";
                      } catch (error) {
                        statusBox.textContent = "Google Maps could not locate a campus stop: " + error.message;
                      }
                    }
                    window.updateTrip = updateTrip;
                  </script>
                  <script async
                    src="https://maps.googleapis.com/maps/api/js?key=__API_KEY__&callback=initializeMap"
                    onerror="document.getElementById('status').textContent='Google Maps failed to load; check the API key and network access.'">
                  </script>
                </body>
                </html>
                """.replace("__API_KEY__", encodedKey);
    }

    private static String safeId(String id) {
        return id == null ? "" : id;
    }

    private static void addIfPresent(Set<String> ids, String id) {
        if (!id.isBlank()) {
            ids.add(id);
        }
    }

    private static String quote(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (character < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) character));
                    } else {
                        quoted.append(character);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    private record TripView(List<String> route, String currentLocationId,
                            String pickupId, String destinationId) {
    }
}
