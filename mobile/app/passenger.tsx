import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { StyleSheet, View, Text, ScrollView, TouchableOpacity, Alert } from 'react-native';
import { TextInput, Button, Card, Badge } from 'react-native-paper';
import { useRouter, useFocusEffect } from 'expo-router';
import { useAuth } from '../src/context/AuthContext';
import { MapLeafletView } from '../src/components/MapLeafletView';
import { campusApi, routeApi, rideApi, nearbyApi } from '../src/services/api';
import { useLiveLocation } from '../src/hooks/useLiveLocation';
import { CampusLocation, RouteResult, CandidateRide, RideRequest, NearbyDriver } from '../src/types';
import {
  LatLng,
  isOnCampus,
  nearestLocation,
  haversineMeters,
  formatDistance,
  formatEta,
  estimateFareBdt,
  CAMPUS_CENTER,
} from '../src/utils/geo';

const DEMO_POSITION_ID = 'dairy_gate';
const NEARBY_RADIUS_METERS = 3000;

export default function PassengerScreen() {
  const router = useRouter();
  const { user, logout } = useAuth();
  const { permission, location: gps, retry: retryGps } = useLiveLocation();

  const [locations, setLocations] = useState<CampusLocation[]>([]);
  const [pickup, setPickup] = useState<CampusLocation | null>(null);
  const [pickupIsAuto, setPickupIsAuto] = useState(true);
  const [destination, setDestination] = useState<CampusLocation | null>(null);

  const [editing, setEditing] = useState<'pickup' | 'destination' | null>(null);
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<CampusLocation[]>([]);

  const [passengerCount, setPassengerCount] = useState(1);
  const [nearbyDrivers, setNearbyDrivers] = useState<NearbyDriver[]>([]);
  const [selectedDriverId, setSelectedDriverId] = useState<string | null>(null);
  const [routeResult, setRouteResult] = useState<RouteResult | null>(null);
  const [candidates, setCandidates] = useState<CandidateRide[]>([]);
  const [requesting, setRequesting] = useState(false);
  const [activeRide, setActiveRide] = useState<RideRequest | null>(null);

  useEffect(() => {
    campusApi.getLocations().then(setLocations).catch(e => console.error('Failed to load campus locations', e));
  }, []);

  // Refresh the "active ride" banner whenever this screen comes back into focus
  useFocusEffect(
    useCallback(() => {
      rideApi
        .getMyRequests()
        .then(reqs => setActiveRide(reqs.find(r => !['COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED'].includes(r.status)) || null))
        .catch(() => {});
    }, [])
  );

  // Real GPS on campus; a demo position at Dairy Gate when the phone is far from JU (e.g. testing from home)
  const offCampus = gps != null && !isOnCampus(gps);
  const demoLocation = locations.find(l => l.id === DEMO_POSITION_ID) || null;
  const myPosition: LatLng | null = useMemo(() => {
    if (gps && !offCampus) return gps;
    if (offCampus && demoLocation) return { latitude: demoLocation.latitude, longitude: demoLocation.longitude };
    return null;
  }, [gps, offCampus, demoLocation]);

  // Auto pickup = campus landmark nearest to the passenger
  useEffect(() => {
    if (pickupIsAuto && myPosition && locations.length > 0) {
      setPickup(nearestLocation(myPosition, locations));
    }
  }, [pickupIsAuto, myPosition, locations]);

  // Poll nearby online rickshaws around the passenger (or the pickup / campus center without GPS)
  const searchCenter: LatLng = myPosition || pickup || CAMPUS_CENTER;
  useEffect(() => {
    let cancelled = false;
    const load = () =>
      nearbyApi
        .getNearbyDrivers(searchCenter.latitude, searchCenter.longitude, NEARBY_RADIUS_METERS)
        .then(list => !cancelled && setNearbyDrivers(list))
        .catch(() => {});
    load();
    const interval = setInterval(load, 5000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [searchCenter.latitude, searchCenter.longitude]);

  // Drop the selection if that rickshaw went offline or moved away
  useEffect(() => {
    if (selectedDriverId && !nearbyDrivers.some(d => d.driverId === selectedDriverId)) {
      setSelectedDriverId(null);
    }
  }, [nearbyDrivers, selectedDriverId]);

  // Dijkstra route + matching-engine recommendations once both ends are known
  useEffect(() => {
    setRouteResult(null);
    setCandidates([]);
    if (!pickup || !destination || pickup.id === destination.id) return;
    routeApi
      .getShortestPath(pickup.id, destination.id)
      .then(route => {
        if (route?.pathVertices) {
          route.coordinates = route.pathVertices.map(v => [v.latitude, v.longitude] as [number, number]);
        }
        setRouteResult(route);
      })
      .catch(() => {});
    routeApi
      .getRideOptions(pickup.id, destination.id, passengerCount)
      .then(c => setCandidates(c || []))
      .catch(() => {});
  }, [pickup?.id, destination?.id, passengerCount]);

  const recommendedVehicleId = candidates.find(c => c.recommended)?.vehicleId;

  // Trie autocomplete search
  const handleSearch = async (text: string) => {
    setQuery(text);
    if (!text.trim()) {
      setSuggestions([]);
      return;
    }
    try {
      setSuggestions(await campusApi.searchLocations(text.trim(), 6));
    } catch (e) {
      setSuggestions([]);
    }
  };

  const chooseLocation = (loc: CampusLocation) => {
    if (editing === 'pickup') {
      setPickup(loc);
      setPickupIsAuto(false);
    } else {
      setDestination(loc);
    }
    setEditing(null);
    setQuery('');
    setSuggestions([]);
  };

  const useMyLocationForPickup = () => {
    setPickupIsAuto(true);
    setEditing(null);
    setQuery('');
    setSuggestions([]);
  };

  const selectedDriver = nearbyDrivers.find(d => d.driverId === selectedDriverId) || null;

  const handleRequestRide = async () => {
    if (!pickup || !destination) {
      Alert.alert('Where to?', 'Choose a destination first (search it or tap a landmark on the map).');
      return;
    }
    if (pickup.id === destination.id) {
      Alert.alert('Invalid trip', 'Pickup and destination cannot be the same place.');
      return;
    }
    if (selectedDriver && selectedDriver.availableSeats < passengerCount) {
      Alert.alert('Not enough seats', `${selectedDriver.rickshawNumber} has only ${selectedDriver.availableSeats} seat(s) free.`);
      return;
    }

    setRequesting(true);
    try {
      const res = await rideApi.createRideRequest({
        pickupLocationId: pickup.id,
        destinationLocationId: destination.id,
        passengerCount,
        selectedVehicleId: selectedDriver?.vehicleId,
        // Only send the exact pin when it's the real GPS position
        passengerLatitude: pickupIsAuto && gps && !offCampus ? gps.latitude : undefined,
        passengerLongitude: pickupIsAuto && gps && !offCampus ? gps.longitude : undefined,
      });
      router.push({ pathname: '/active-ride', params: { id: res.id } });
    } catch (e: any) {
      Alert.alert('Request Failed', e.response?.data?.message || 'Could not send ride request');
    } finally {
      setRequesting(false);
    }
  };

  const fare = routeResult ? estimateFareBdt(routeResult.totalDistanceMeters) : null;
  const fitKey = `${myPosition ? 'me' : 'nome'}|${destination?.id || ''}|${pickup?.id || ''}`;

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      {/* Header */}
      <View style={styles.topBar}>
        <View>
          <Text style={styles.greeting}>Hi, {user?.fullName?.split(' ')[0] || 'Student'} 👋</Text>
          <Text style={styles.roleSub}>Where are you going today?</Text>
        </View>
        <Button
          compact
          mode="outlined"
          textColor="#dc2626"
          style={styles.logoutBtn}
          onPress={async () => {
            await logout();
            router.replace('/login');
          }}
        >
          Logout
        </Button>
      </View>

      {activeRide && (
        <TouchableOpacity
          style={styles.activeBanner}
          onPress={() => router.push({ pathname: '/active-ride', params: { id: activeRide.id } })}
        >
          <Text style={styles.activeBannerText}>
            🔔 You have an active ride ({activeRide.status.replace(/_/g, ' ')}) — tap to track it
          </Text>
        </TouchableOpacity>
      )}

      {/* Location permission / off-campus notices */}
      {(permission === 'denied' || permission === 'services_off') && (
        <View style={styles.warnBox}>
          <Text style={styles.warnText}>
            {permission === 'services_off'
              ? '📡 Location (GPS) is turned off on your phone. Turn it on to find rickshaws near you.'
              : '📍 Location permission denied. Allow it to auto-detect your pickup and see nearby rickshaws.'}
          </Text>
          <Button compact mode="contained-tonal" onPress={retryGps} style={{ marginTop: 6 }}>
            Try again
          </Button>
        </View>
      )}
      {permission === 'checking' && <Text style={styles.infoText}>📡 Finding your location…</Text>}
      {offCampus && gps && (
        <View style={styles.infoBox}>
          <Text style={styles.infoBoxText}>
            You are {formatDistance(haversineMeters(gps, CAMPUS_CENTER))} away from JU campus, so a demo position at Dairy Gate is used.
          </Text>
        </View>
      )}

      {/* Live map */}
      <MapLeafletView
        locations={locations}
        userLocation={myPosition}
        nearbyDrivers={nearbyDrivers}
        selectedDriverId={selectedDriverId}
        onDriverPress={d => setSelectedDriverId(prev => (prev === d.driverId ? null : d.driverId))}
        onLocationSelect={loc => {
          setDestination(loc);
          setEditing(null);
        }}
        pickup={pickup}
        destination={destination}
        routeCoordinates={routeResult?.coordinates}
        fitKey={fitKey}
        height={300}
      />
      <Text style={styles.mapHint}>Tap a 🛺 to pick that rickshaw · tap a landmark dot to set it as destination</Text>

      {/* Pickup / destination */}
      <Card style={styles.card}>
        <Card.Content>
          <TouchableOpacity style={styles.placeRow} onPress={() => setEditing(editing === 'pickup' ? null : 'pickup')}>
            <Text style={styles.placeIcon}>🟢</Text>
            <View style={{ flex: 1 }}>
              <Text style={styles.placeLabel}>Pickup{pickupIsAuto && myPosition ? ' · your location' : ''}</Text>
              <Text style={styles.placeValue}>{pickup ? pickup.name : 'Choose pickup point'}</Text>
            </View>
            <Text style={styles.changeLink}>Change</Text>
          </TouchableOpacity>
          <View style={styles.placeDivider} />
          <TouchableOpacity style={styles.placeRow} onPress={() => setEditing(editing === 'destination' ? null : 'destination')}>
            <Text style={styles.placeIcon}>🏁</Text>
            <View style={{ flex: 1 }}>
              <Text style={styles.placeLabel}>Destination</Text>
              <Text style={[styles.placeValue, !destination && styles.placeholder]}>
                {destination ? destination.name : 'Where to?'}
              </Text>
            </View>
            <Text style={styles.changeLink}>{destination ? 'Change' : 'Search'}</Text>
          </TouchableOpacity>

          {editing && (
            <View style={{ marginTop: 10 }}>
              <TextInput
                autoFocus
                placeholder={editing === 'pickup' ? 'Search pickup e.g. Dairy, CSE…' : 'Search e.g. Library, Bottola, Hall…'}
                value={query}
                onChangeText={handleSearch}
                mode="outlined"
                dense
                activeOutlineColor="#16a34a"
                style={{ backgroundColor: '#ffffff' }}
              />
              {editing === 'pickup' && myPosition && (
                <TouchableOpacity style={styles.suggestionItem} onPress={useMyLocationForPickup}>
                  <Text style={styles.suggestionTitle}>📍 Use my current location</Text>
                </TouchableOpacity>
              )}
              {suggestions.map(loc => (
                <TouchableOpacity key={loc.id} style={styles.suggestionItem} onPress={() => chooseLocation(loc)}>
                  <Text style={styles.suggestionTitle}>
                    {loc.name} ({loc.bengaliName})
                  </Text>
                  <Text style={styles.suggestionSub}>
                    {loc.category}
                    {loc.rickshawStand ? ' • 🛺 Rickshaw Stand' : ''}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          )}

          {routeResult && (
            <View style={styles.routeSummary}>
              <Text style={styles.routeMetric}>{formatDistance(routeResult.totalDistanceMeters)}</Text>
              <Text style={styles.routeDot}>•</Text>
              <Text style={styles.routeMetric}>{formatEta(routeResult.estimatedTravelTimeSeconds)}</Text>
              <Text style={styles.routeDot}>•</Text>
              <Text style={[styles.routeMetric, { color: '#16a34a' }]}>৳ {fare}</Text>
              <Text style={styles.routeAlgo}>Dijkstra shortest path</Text>
            </View>
          )}

          <View style={styles.seatsRow}>
            <Text style={styles.placeLabel}>Seats</Text>
            <View style={styles.counterRow}>
              {[1, 2, 3].map(count => (
                <Button
                  key={count}
                  compact
                  mode={passengerCount === count ? 'contained' : 'outlined'}
                  buttonColor={passengerCount === count ? '#16a34a' : undefined}
                  textColor={passengerCount === count ? '#ffffff' : '#334155'}
                  style={styles.countBtn}
                  onPress={() => setPassengerCount(count)}
                >
                  {count}
                </Button>
              ))}
            </View>
          </View>
        </Card.Content>
      </Card>

      {/* Nearby rickshaws */}
      <Text style={styles.sectionHeader}>Nearby rickshaws ({nearbyDrivers.length})</Text>
      {nearbyDrivers.length === 0 ? (
        <View style={styles.emptyBox}>
          <Text style={styles.emptyText}>
            No online rickshaw within {formatDistance(NEARBY_RADIUS_METERS)} right now. You can still send a request — it goes to every driver who comes online.
          </Text>
        </View>
      ) : (
        nearbyDrivers.map(d => {
          const selected = d.driverId === selectedDriverId;
          const full = d.availableSeats < passengerCount;
          return (
            <TouchableOpacity
              key={d.driverId}
              activeOpacity={0.8}
              onPress={() => setSelectedDriverId(selected ? null : d.driverId)}
              style={[styles.driverCard, selected && styles.driverCardSelected, full && styles.driverCardFull]}
            >
              <Text style={styles.driverEmoji}>🛺</Text>
              <View style={{ flex: 1 }}>
                <View style={styles.driverTitleRow}>
                  <Text style={styles.driverName}>{d.driverName}</Text>
                  {d.vehicleId === recommendedVehicleId && <Badge style={styles.recBadge}>⭐ Best match</Badge>}
                </View>
                <Text style={styles.driverSub}>
                  {d.rickshawNumber}
                  {d.nearestLocationName ? ` • near ${d.nearestLocationName}` : ''}
                </Text>
                <Text style={styles.driverMeta}>
                  {formatDistance(d.distanceMeters)} away • ~{formatEta(d.etaSeconds)} •{' '}
                  {d.onTrip ? `sharing, ${d.availableSeats} seat(s) free` : `${d.availableSeats}/${d.capacity} seats free`}
                </Text>
              </View>
              <View style={[styles.radio, selected && styles.radioOn]} />
            </TouchableOpacity>
          );
        })
      )}

      <Button
        mode="contained"
        style={styles.requestBtn}
        contentStyle={{ paddingVertical: 6 }}
        loading={requesting}
        disabled={requesting || !pickup || !destination || !!activeRide}
        onPress={handleRequestRide}
      >
        {activeRide
          ? 'You already have an active ride'
          : !destination
          ? 'Choose a destination'
          : selectedDriver
          ? `Request ${selectedDriver.rickshawNumber}${fare ? ` · ৳${fare}` : ''}`
          : `Request any nearby rickshaw${fare ? ` · ৳${fare}` : ''}`}
      </Button>
      {selectedDriver && (
        <Text style={styles.requestHint}>Only {selectedDriver.driverName} will get this request. Tap the card again to send to everyone.</Text>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, backgroundColor: '#f8fafc', paddingBottom: 40 },
  topBar: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
  greeting: { fontSize: 20, fontWeight: 'bold', color: '#0f172a' },
  roleSub: { fontSize: 12, color: '#64748b' },
  logoutBtn: { borderColor: '#fca5a5', borderRadius: 8 },
  activeBanner: {
    backgroundColor: '#dcfce7',
    borderColor: '#86efac',
    borderWidth: 1,
    padding: 10,
    borderRadius: 8,
    marginBottom: 12,
  },
  activeBannerText: { color: '#166534', fontSize: 13, fontWeight: '600' },
  warnBox: { backgroundColor: '#fef3c7', borderColor: '#fcd34d', borderWidth: 1, padding: 10, borderRadius: 8, marginBottom: 10 },
  warnText: { color: '#92400e', fontSize: 13 },
  infoBox: { backgroundColor: '#eff6ff', borderColor: '#bfdbfe', borderWidth: 1, padding: 8, borderRadius: 8, marginBottom: 10 },
  infoBoxText: { color: '#1e40af', fontSize: 12 },
  infoText: { fontSize: 12, color: '#64748b', marginBottom: 8 },
  mapHint: { fontSize: 11, color: '#94a3b8', textAlign: 'center', marginTop: 6 },
  card: { backgroundColor: '#ffffff', borderRadius: 14, marginTop: 12, elevation: 2 },
  placeRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 6 },
  placeIcon: { fontSize: 16, width: 28 },
  placeLabel: { fontSize: 11, color: '#64748b', fontWeight: '600' },
  placeValue: { fontSize: 15, color: '#0f172a', fontWeight: '600' },
  placeholder: { color: '#94a3b8' },
  changeLink: { fontSize: 12, color: '#16a34a', fontWeight: '700' },
  placeDivider: { height: 1, backgroundColor: '#f1f5f9', marginLeft: 28 },
  suggestionItem: { padding: 10, borderBottomWidth: 1, borderBottomColor: '#f1f5f9' },
  suggestionTitle: { fontSize: 13, fontWeight: '600', color: '#0f172a' },
  suggestionSub: { fontSize: 11, color: '#64748b' },
  routeSummary: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    backgroundColor: '#f0fdf4',
    borderRadius: 8,
    padding: 8,
    marginTop: 10,
  },
  routeMetric: { fontSize: 14, fontWeight: 'bold', color: '#166534' },
  routeDot: { marginHorizontal: 6, color: '#86efac' },
  routeAlgo: { fontSize: 10, color: '#64748b', marginLeft: 'auto' },
  seatsRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 },
  counterRow: { flexDirection: 'row', gap: 6 },
  countBtn: { borderRadius: 6, minWidth: 44 },
  sectionHeader: {
    fontSize: 13,
    fontWeight: '700',
    color: '#334155',
    marginTop: 18,
    marginBottom: 8,
    textTransform: 'uppercase',
  },
  emptyBox: { backgroundColor: '#ffffff', padding: 12, borderRadius: 10, borderWidth: 1, borderColor: '#e2e8f0' },
  emptyText: { fontSize: 12, color: '#64748b', textAlign: 'center' },
  driverCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ffffff',
    borderColor: '#e2e8f0',
    borderWidth: 1.5,
    padding: 12,
    borderRadius: 12,
    marginBottom: 8,
  },
  driverCardSelected: { borderColor: '#16a34a', backgroundColor: '#f0fdf4' },
  driverCardFull: { opacity: 0.55 },
  driverEmoji: { fontSize: 28, marginRight: 10 },
  driverTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  driverName: { fontSize: 15, fontWeight: 'bold', color: '#0f172a' },
  recBadge: { backgroundColor: '#f59e0b' },
  driverSub: { fontSize: 12, color: '#475569' },
  driverMeta: { fontSize: 12, color: '#16a34a', fontWeight: '600', marginTop: 2 },
  radio: { width: 20, height: 20, borderRadius: 10, borderWidth: 2, borderColor: '#cbd5e1' },
  radioOn: { borderColor: '#16a34a', backgroundColor: '#16a34a' },
  requestBtn: { backgroundColor: '#16a34a', borderRadius: 10, marginTop: 14 },
  requestHint: { fontSize: 11, color: '#64748b', textAlign: 'center', marginTop: 6 },
});
