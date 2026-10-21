import React, { useRef, useEffect, useCallback } from 'react';
import { StyleSheet, View } from 'react-native';
import { WebView, WebViewMessageEvent } from 'react-native-webview';
import { CampusLocation, NearbyDriver } from '../types';
import { LatLng } from '../utils/geo';

interface MapProps {
  locations?: CampusLocation[];
  routeCoordinates?: [number, number][]; // [[lat, lng], ...]
  pickup?: CampusLocation | null;
  destination?: CampusLocation | null;
  driverLocation?: { latitude: number; longitude: number; label?: string } | null;
  userLocation?: LatLng | null; // blue "you are here" dot
  nearbyDrivers?: NearbyDriver[];
  selectedDriverId?: string | null;
  onDriverPress?: (driver: NearbyDriver) => void;
  onLocationSelect?: (location: CampusLocation) => void;
  // The map re-fits its viewport only when this key changes, so live updates don't fight the user's panning
  fitKey?: string;
  height?: number;
}

const HTML = `
<!DOCTYPE html>
<html>
  <head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <style>
      body, html, #map { margin: 0; padding: 0; width: 100%; height: 100%; }
      .rickshaw-icon { font-size: 24px; text-shadow: 0 1px 3px rgba(0,0,0,0.5); }
      .landmark-label { font-size: 11px; font-weight: bold; background: white; padding: 2px 4px; border-radius: 4px; border: 1px solid #16a34a; white-space: nowrap; }
      .nearby { display: flex; flex-direction: column; align-items: center; }
      .nearby .emoji { font-size: 22px; line-height: 24px; text-shadow: 0 1px 3px rgba(0,0,0,0.45); }
      .nearby .tag { font-size: 9px; font-weight: bold; background: #ffffff; color: #0f172a; border: 1px solid #94a3b8; border-radius: 6px; padding: 0 3px; white-space: nowrap; }
      .nearby.busy .tag { color: #92400e; border-color: #f59e0b; }
      .nearby.selected .emoji { font-size: 30px; line-height: 32px; }
      .nearby.selected .tag { background: #16a34a; color: #ffffff; border-color: #15803d; }
    </style>
  </head>
  <body>
    <div id="map"></div>
    <script>
      const map = L.map('map', { zoomControl: false }).setView([23.8812, 90.2673], 15);
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '© OpenStreetMap'
      }).addTo(map);

      let layers = [];
      let routeLayer = null;
      let lastFitKey = null;

      function send(msg) {
        if (window.ReactNativeWebView) window.ReactNativeWebView.postMessage(JSON.stringify(msg));
      }

      function add(layer) { layer.addTo(map); layers.push(layer); return layer; }

      function updateData(data) {
        layers.forEach(l => map.removeLayer(l));
        layers = [];
        const fitPoints = [];

        (data.locations || []).forEach(loc => {
          const m = add(L.circleMarker([loc.latitude, loc.longitude], {
            radius: loc.rickshawStand ? 7 : 5,
            fillColor: loc.rickshawStand ? '#16a34a' : '#2563eb',
            color: '#ffffff', weight: 2, fillOpacity: 0.9
          }));
          m.bindTooltip(loc.name);
          m.on('click', () => send({ type: 'location', id: loc.id }));
        });

        if (data.pickup) {
          add(L.marker([data.pickup.latitude, data.pickup.longitude], {
            icon: L.divIcon({ className: 'landmark-label', html: '📍 ' + data.pickup.name, iconSize: null, iconAnchor: [0, 24] })
          }));
          fitPoints.push([data.pickup.latitude, data.pickup.longitude]);
        }

        if (data.destination) {
          add(L.marker([data.destination.latitude, data.destination.longitude], {
            icon: L.divIcon({ className: 'landmark-label', html: '🏁 ' + data.destination.name, iconSize: null, iconAnchor: [0, 24] })
          }));
          fitPoints.push([data.destination.latitude, data.destination.longitude]);
        }

        if (routeLayer) { map.removeLayer(routeLayer); routeLayer = null; }
        if (data.routeCoordinates && data.routeCoordinates.length > 1) {
          routeLayer = L.polyline(data.routeCoordinates, { color: '#16a34a', weight: 5, opacity: 0.85 }).addTo(map);
          data.routeCoordinates.forEach(c => fitPoints.push(c));
        }

        (data.nearbyDrivers || []).forEach(d => {
          const selected = d.driverId === data.selectedDriverId;
          const cls = 'nearby' + (selected ? ' selected' : '') + (d.availableSeats < 1 || d.onTrip ? ' busy' : '');
          const m = add(L.marker([d.latitude, d.longitude], {
            icon: L.divIcon({
              className: '',
              html: '<div class="' + cls + '"><div class="emoji">🛺</div><div class="tag">' + d.rickshawNumber + '</div></div>',
              iconSize: [60, 40],
              iconAnchor: [30, 20]
            }),
            zIndexOffset: selected ? 1000 : 0
          }));
          m.on('click', () => send({ type: 'driver', id: d.driverId }));
          fitPoints.push([d.latitude, d.longitude]);
        });

        if (data.driverLocation && data.driverLocation.latitude) {
          const dm = add(L.marker([data.driverLocation.latitude, data.driverLocation.longitude], {
            icon: L.divIcon({ className: 'rickshaw-icon', html: '🛺', iconSize: [28, 28], iconAnchor: [14, 14] }),
            zIndexOffset: 1000
          }));
          dm.bindTooltip(data.driverLocation.label || 'Rickshaw', { permanent: true, direction: 'top', offset: [0, -12] });
          fitPoints.push([data.driverLocation.latitude, data.driverLocation.longitude]);
        }

        if (data.userLocation) {
          add(L.circle([data.userLocation.latitude, data.userLocation.longitude], {
            radius: 25, color: '#2563eb', weight: 1, fillColor: '#3b82f6', fillOpacity: 0.15
          }));
          add(L.circleMarker([data.userLocation.latitude, data.userLocation.longitude], {
            radius: 7, color: '#ffffff', weight: 3, fillColor: '#2563eb', fillOpacity: 1
          })).bindTooltip('You');
          fitPoints.push([data.userLocation.latitude, data.userLocation.longitude]);
        }

        if (data.fitKey !== lastFitKey) {
          lastFitKey = data.fitKey;
          if (fitPoints.length > 1) map.fitBounds(fitPoints, { padding: [40, 40], maxZoom: 17 });
          else if (fitPoints.length === 1) map.setView(fitPoints[0], 17);
        }
      }

      function onMessage(e) { try { updateData(JSON.parse(e.data)); } catch (err) {} }
      document.addEventListener('message', onMessage);
      window.addEventListener('message', onMessage);
      send({ type: 'ready' });
    </script>
  </body>
</html>
`;

export const MapLeafletView: React.FC<MapProps> = ({
  locations = [],
  routeCoordinates = [],
  pickup,
  destination,
  driverLocation,
  userLocation,
  nearbyDrivers = [],
  selectedDriverId,
  onDriverPress,
  onLocationSelect,
  fitKey = 'default',
  height = 240,
}) => {
  const webViewRef = useRef<WebView>(null);

  const pushData = useCallback(() => {
    webViewRef.current?.postMessage(
      JSON.stringify({
        locations,
        routeCoordinates,
        pickup,
        destination,
        driverLocation,
        userLocation,
        nearbyDrivers,
        selectedDriverId,
        fitKey,
      })
    );
  }, [locations, routeCoordinates, pickup, destination, driverLocation, userLocation, nearbyDrivers, selectedDriverId, fitKey]);

  useEffect(() => {
    pushData();
  }, [pushData]);

  const handleMessage = (event: WebViewMessageEvent) => {
    try {
      const msg = JSON.parse(event.nativeEvent.data);
      if (msg.type === 'ready') {
        pushData();
      } else if (msg.type === 'driver') {
        const driver = nearbyDrivers.find(d => d.driverId === msg.id);
        if (driver) onDriverPress?.(driver);
      } else if (msg.type === 'location') {
        const loc = locations.find(l => l.id === msg.id);
        if (loc) onLocationSelect?.(loc);
      }
    } catch (e) {
      // ignore malformed messages
    }
  };

  return (
    <View style={[styles.container, { height }]}>
      <WebView
        ref={webViewRef}
        originWhitelist={['*']}
        source={{ html: HTML }}
        style={styles.webview}
        javaScriptEnabled={true}
        domStorageEnabled={true}
        onLoadEnd={pushData}
        onMessage={handleMessage}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: '100%',
    borderRadius: 12,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: '#e2e8f0',
    backgroundColor: '#f1f5f9',
  },
  webview: {
    flex: 1,
  },
});
