import React, { useState, useEffect, useRef, useCallback } from 'react';
import { StyleSheet, View, Text, ScrollView, Alert, Linking } from 'react-native';
import { Button, Card, Switch, Badge, Divider, Chip } from 'react-native-paper';
import { useRouter } from 'expo-router';
import { useAuth } from '../src/context/AuthContext';
import { driverApi, campusApi } from '../src/services/api';
import { useLiveLocation } from '../src/hooks/useLiveLocation';
import { Vehicle, DriverTrip, RideRequest, CampusLocation } from '../src/types';
import { MapLeafletView } from '../src/components/MapLeafletView';
import { isOnCampus, formatDistance } from '../src/utils/geo';

export default function DriverScreen() {
  const router = useRouter();
  const { user, logout } = useAuth();

  const [vehicle, setVehicle] = useState<Vehicle | null>(null);
  const [activeTrip, setActiveTrip] = useState<DriverTrip | null>(null);
  const [activeRequests, setActiveRequests] = useState<RideRequest[]>([]);
  const [incomingRequests, setIncomingRequests] = useState<RideRequest[]>([]);
  const [locations, setLocations] = useState<CampusLocation[]>([]);
  const [isOnline, setIsOnline] = useState(true);
  const [standId, setStandId] = useState('dairy_gate');
  const [processingId, setProcessingId] = useState<string | null>(null);

  // GPS only matters while online
  const { permission, location: gps, retry: retryGps } = useLiveLocation({ enabled: isOnline, distanceInterval: 10, timeInterval: 5000 });
  const gpsUsable = permission === 'granted' && gps != null && isOnCampus(gps);
  const lastSent = useRef<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [status, reqs] = await Promise.all([driverApi.getDriverStatus(), driverApi.getIncomingRequests()]);
      if (status.vehicle) {
        setVehicle(status.vehicle);
        setIsOnline(status.vehicle.isOnline);
      }
      setActiveTrip(status.activeTrip || null);
      setActiveRequests(status.activeRequests || []);
      setIncomingRequests(reqs);
    } catch (e) {
      // keep last state on transient network errors
    }
  }, []);

  useEffect(() => {
    campusApi.getLocations().then(setLocations).catch(() => {});
    driverApi
      .getDriverStatus()
      .then(status => {
        if (status.vehicle?.currentLocation) setStandId(status.vehicle.currentLocation.id);
      })
      .catch(() => {});
    refresh();
    const interval = setInterval(refresh, 4000);
    return () => clearInterval(interval);
  }, [refresh]);

  // Stream live GPS to the backend; the server snaps it to the nearest campus landmark
  useEffect(() => {
    if (!isOnline || !gpsUsable || !gps) return;
    const key = `${gps.latitude.toFixed(5)},${gps.longitude.toFixed(5)}`;
    if (key === lastSent.current) return;
    lastSent.current = key;
    driverApi
      .updateLocation({
        driverId: user?.userId || user?.id,
        vehicleId: vehicle?.id,
        latitude: gps.latitude,
        longitude: gps.longitude,
        locationSource: 'LIVE_PHONE',
      })
      .catch(() => {
        lastSent.current = null;
      });
  }, [gps, gpsUsable, isOnline, vehicle?.id]);

  const handleToggleOnline = async (val: boolean) => {
    setIsOnline(val);
    try {
      // With GPS the position comes from the phone; otherwise place the rickshaw at the chosen stand
      const updated = await driverApi.setAvailability(val, gpsUsable ? undefined : standId);
      setVehicle(updated);
      lastSent.current = null;
    } catch (e: any) {
      Alert.alert('Error', e.response?.data?.message || 'Failed to update availability');
      setIsOnline(!val);
    }
  };

  const handleSelectStand = async (locId: string) => {
    setStandId(locId);
    try {
      setVehicle(await driverApi.setAvailability(isOnline, locId));
    } catch (e: any) {
      Alert.alert('Error', e.response?.data?.message || 'Could not update location');
    }
  };

  const handleAccept = async (req: RideRequest) => {
    setProcessingId(req.id);
    try {
      await driverApi.acceptRequest(req.id);
      Alert.alert('Ride accepted ✅', `Go to ${req.pickupLocationName} to pick up ${req.passengerName}.`);
      await refresh();
    } catch (e: any) {
      Alert.alert('Could not accept', e.response?.data?.message || 'This ride is no longer available');
      await refresh();
    } finally {
      setProcessingId(null);
    }
  };

  const handleReject = async (req: RideRequest) => {
    setProcessingId(req.id);
    try {
      await driverApi.rejectRequest(req.id, 'Driver declined');
      await refresh();
    } catch (e: any) {
      Alert.alert('Error', e.response?.data?.message || 'Could not reject request');
    } finally {
      setProcessingId(null);
    }
  };

  const handleTripStatusUpdate = async (status: string) => {
    if (!activeTrip) return;
    try {
      const target = status === 'COMPLETED' ? activeRequests[0]?.destinationLocationId : undefined;
      await driverApi.updateTripStatus(activeTrip.id, status, target);
      await refresh();
    } catch (e: any) {
      Alert.alert('Error', e.response?.data?.message || 'Failed to update trip status');
    }
  };

  // Where the rickshaw is drawn: live GPS, else last known server position, else the selected stand
  const stand = locations.find(l => l.id === standId);
  const myPos = gpsUsable && gps
    ? gps
    : vehicle?.currentLatitude && vehicle?.currentLongitude
    ? { latitude: vehicle.currentLatitude, longitude: vehicle.currentLongitude }
    : stand || null;

  // Map focus: the passenger I'm serving, or the closest new request
  const focus = activeRequests[0] || incomingRequests[0] || null;
  const focusPickup = focus
    ? locations.find(l => l.id === focus.pickupLocationId) || null
    : null;
  const focusDest = focus ? locations.find(l => l.id === focus.destinationLocationId) || null : null;
  const goingToPickup = activeTrip?.status === 'ARRIVING';

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <View style={styles.topBar}>
        <View>
          <Text style={styles.greeting}>{user?.fullName || 'Rickshaw Driver'}</Text>
          <Text style={styles.roleSub}>🛺 {vehicle?.rickshawNumber || user?.rickshawNumber || 'JU Rickshaw'}</Text>
        </View>
        <Button
          compact
          mode="outlined"
          textColor="#dc2626"
          style={styles.logoutBtn}
          onPress={async () => {
            if (isOnline) await driverApi.setAvailability(false).catch(() => {});
            await logout();
            router.replace('/login');
          }}
        >
          Logout
        </Button>
      </View>

      {/* Online + location source */}
      <Card style={styles.card}>
        <Card.Content>
          <View style={styles.switchRow}>
            <View style={{ flex: 1 }}>
              <Text style={styles.cardTitle}>{isOnline ? '🟢 Online' : '⚫ Offline'}</Text>
              <Text style={styles.switchSub}>
                {isOnline ? 'Passengers nearby can see and request you' : 'You are hidden from passengers'}
              </Text>
            </View>
            <Switch value={isOnline} onValueChange={handleToggleOnline} color="#16a34a" />
          </View>

          {isOnline && (
            <>
              <Divider style={{ marginVertical: 12 }} />
              {gpsUsable ? (
                <Text style={styles.gpsLive}>📡 Live GPS is on — your position updates automatically</Text>
              ) : (
                <>
                  <Text style={styles.gpsWarn}>
                    {permission === 'denied'
                      ? '📍 Location permission denied — choose your stand manually.'
                      : permission === 'services_off'
                      ? '📡 Phone GPS is off — choose your stand manually.'
                      : permission === 'checking'
                      ? '📡 Getting GPS fix…'
                      : '🗺️ You are outside JU campus — choose a stand to appear on the campus map.'}
                  </Text>
                  {(permission === 'denied' || permission === 'services_off') && (
                    <Button compact mode="text" onPress={retryGps}>
                      Enable GPS
                    </Button>
                  )}
                  <View style={styles.chipsRow}>
                    {locations
                      .filter(l => l.rickshawStand)
                      .map(loc => (
                        <Chip key={loc.id} style={styles.chip} selected={standId === loc.id} onPress={() => handleSelectStand(loc.id)}>
                          {loc.name}
                        </Chip>
                      ))}
                  </View>
                </>
              )}
            </>
          )}
        </Card.Content>
      </Card>

      <MapLeafletView
        locations={focus ? [] : locations}
        driverLocation={myPos ? { ...myPos, label: 'You' } : null}
        userLocation={
          focus?.passengerLatitude && focus.passengerLongitude && (!activeTrip || goingToPickup)
            ? { latitude: focus.passengerLatitude, longitude: focus.passengerLongitude }
            : null
        }
        pickup={focusPickup}
        destination={focusDest}
        fitKey={`${focus?.id || 'none'}|${activeTrip?.status || ''}`}
        height={240}
      />

      {/* Active trip */}
      {activeTrip && (
        <Card style={[styles.card, styles.tripCard]}>
          <Card.Content>
            <View style={styles.tripHeader}>
              <Text style={styles.tripTitle}>{goingToPickup ? '🚦 Going to pickup' : '🚀 Trip in progress'}</Text>
              <Badge style={styles.badgeTrip}>{`${activeTrip.occupiedSeats}/${vehicle?.capacity || 3} seats`}</Badge>
            </View>

            {activeRequests.map(r => (
              <View key={r.id} style={styles.passengerRow}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.passengerName}>👤 {r.passengerName}</Text>
                  <Text style={styles.routeText}>
                    📍 {r.pickupLocationName} ➔ 🏁 {r.destinationLocationName}
                  </Text>
                  <Text style={styles.fareText}>
                    ৳ {r.estimatedFareBdt} • {r.passengerCount} seat(s)
                  </Text>
                </View>
                {r.passengerPhone ? (
                  <Button compact mode="outlined" icon="phone" onPress={() => Linking.openURL(`tel:${r.passengerPhone}`)}>
                    Call
                  </Button>
                ) : null}
              </View>
            ))}

            {goingToPickup ? (
              <Button mode="contained" style={styles.actionBtn} onPress={() => handleTripStatusUpdate('IN_PROGRESS')}>
                Passenger picked up · Start trip
              </Button>
            ) : (
              <Button mode="contained" buttonColor="#15803d" style={styles.actionBtn} onPress={() => handleTripStatusUpdate('COMPLETED')}>
                Reached destination · Complete trip
              </Button>
            )}
          </Card.Content>
        </Card>
      )}

      {/* Incoming requests */}
      <Text style={styles.sectionHeader}>Ride requests ({incomingRequests.length})</Text>
      {!isOnline ? (
        <Card style={styles.emptyCard}>
          <Card.Content>
            <Text style={styles.emptyText}>Go online to receive ride requests.</Text>
          </Card.Content>
        </Card>
      ) : incomingRequests.length === 0 ? (
        <Card style={styles.emptyCard}>
          <Card.Content>
            <Text style={styles.emptyText}>Waiting for passengers… New requests appear here automatically.</Text>
          </Card.Content>
        </Card>
      ) : (
        incomingRequests.map(req => {
          const targeted = !!req.driverId;
          return (
            <Card key={req.id} style={[styles.reqCard, targeted && styles.reqCardTargeted]}>
              <Card.Content>
                <View style={styles.reqHeader}>
                  <Text style={styles.passengerName}>👤 {req.passengerName}</Text>
                  <Badge style={targeted ? styles.targetBadge : styles.openBadge}>{targeted ? 'Sent to you' : 'Open request'}</Badge>
                </View>
                <Text style={styles.routeText}>
                  📍 {req.pickupLocationName} ➔ 🏁 {req.destinationLocationName}
                </Text>
                <Text style={styles.fareText}>
                  ৳ {req.estimatedFareBdt} • {req.passengerCount} seat(s)
                  {req.driverToPickupMeters != null ? ` • pickup ${formatDistance(req.driverToPickupMeters)} away` : ''}
                </Text>

                <View style={styles.btnRow}>
                  <Button
                    mode="contained"
                    style={styles.acceptBtn}
                    loading={processingId === req.id}
                    disabled={processingId !== null}
                    onPress={() => handleAccept(req)}
                  >
                    Accept
                  </Button>
                  <Button
                    mode="outlined"
                    textColor="#dc2626"
                    style={styles.rejectBtn}
                    disabled={processingId !== null}
                    onPress={() => handleReject(req)}
                  >
                    {targeted ? 'Reject' : 'Ignore'}
                  </Button>
                </View>
              </Card.Content>
            </Card>
          );
        })
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
  card: { backgroundColor: '#ffffff', borderRadius: 14, marginBottom: 14, elevation: 2 },
  cardTitle: { fontSize: 16, fontWeight: 'bold', color: '#0f172a' },
  switchRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  switchSub: { fontSize: 12, color: '#64748b', marginTop: 2 },
  gpsLive: { fontSize: 13, color: '#15803d', fontWeight: '600' },
  gpsWarn: { fontSize: 12, color: '#92400e', marginBottom: 8 },
  chipsRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  chip: { backgroundColor: '#f1f5f9' },
  sectionHeader: { fontSize: 14, fontWeight: '700', color: '#334155', marginVertical: 10, textTransform: 'uppercase' },
  tripCard: { borderColor: '#86efac', borderWidth: 1.5, backgroundColor: '#f0fdf4', marginTop: 14 },
  tripHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 },
  tripTitle: { fontSize: 16, fontWeight: 'bold', color: '#166534' },
  badgeTrip: { backgroundColor: '#16a34a' },
  passengerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ffffff',
    borderRadius: 10,
    padding: 10,
    marginBottom: 8,
  },
  actionBtn: { backgroundColor: '#16a34a', borderRadius: 8, marginTop: 4 },
  emptyCard: { backgroundColor: '#ffffff', borderRadius: 12 },
  emptyText: { fontSize: 13, color: '#64748b', lineHeight: 18, textAlign: 'center', paddingVertical: 10 },
  reqCard: { backgroundColor: '#ffffff', borderRadius: 12, marginBottom: 10, elevation: 2 },
  reqCardTargeted: { borderColor: '#16a34a', borderWidth: 1.5 },
  reqHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 },
  passengerName: { fontSize: 15, fontWeight: 'bold', color: '#0f172a' },
  targetBadge: { backgroundColor: '#16a34a' },
  openBadge: { backgroundColor: '#2563eb' },
  routeText: { fontSize: 13, color: '#334155', marginBottom: 4 },
  fareText: { fontSize: 13, fontWeight: '600', color: '#16a34a', marginBottom: 8 },
  btnRow: { flexDirection: 'row', gap: 10 },
  acceptBtn: { flex: 1, backgroundColor: '#16a34a', borderRadius: 8 },
  rejectBtn: { borderRadius: 8, borderColor: '#fca5a5' },
});
