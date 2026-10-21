import React, { useState, useEffect } from 'react';
import { StyleSheet, View, Text, ScrollView, Alert, Linking } from 'react-native';
import { Button, Card, Badge, Divider, ActivityIndicator } from 'react-native-paper';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { rideApi, routeApi } from '../src/services/api';
import { RideRequest, RouteResult } from '../src/types';
import { MapLeafletView } from '../src/components/MapLeafletView';
import { useLiveLocation } from '../src/hooks/useLiveLocation';
import { haversineMeters, isOnCampus, formatDistance, formatEta } from '../src/utils/geo';

export default function ActiveRideScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();

  const [ride, setRide] = useState<RideRequest | null>(null);
  const [loading, setLoading] = useState(true);
  const [cancelling, setCancelling] = useState(false);
  const [routeResult, setRouteResult] = useState<RouteResult | null>(null);
  const { location: gps } = useLiveLocation();

  useEffect(() => {
    if (!id) return;
    loadRideDetails();
    const interval = setInterval(loadRideDetails, 3000); // 3-second live refresh
    return () => clearInterval(interval);
  }, [id]);

  const loadRideDetails = async () => {
    try {
      if (!id) return;
      const data = await rideApi.getRequestById(id);
      setRide(data);

      // Load route geometry if not already loaded
      if (!routeResult && data.pickupLocationId && data.destinationLocationId) {
        try {
          const res = await routeApi.getShortestPath(data.pickupLocationId, data.destinationLocationId);
          if (res?.pathVertices) {
            res.coordinates = res.pathVertices.map(v => [v.latitude, v.longitude] as [number, number]);
          }
          setRouteResult(res);
        } catch (e) {
          // ignore
        }
      }
    } catch (e) {
      console.error('Error fetching ride status', e);
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = async () => {
    if (!id) return;
    Alert.alert('Cancel Ride', 'Are you sure you want to cancel this ride request?', [
      { text: 'No, Keep It', style: 'cancel' },
      {
        text: 'Yes, Cancel',
        style: 'destructive',
        onPress: async () => {
          setCancelling(true);
          try {
            await rideApi.cancelRequest(id);
            Alert.alert('Cancelled', 'Your ride request has been cancelled.');
            loadRideDetails();
          } catch (e: any) {
            Alert.alert('Error', e.response?.data?.message || 'Could not cancel ride');
          } finally {
            setCancelling(false);
          }
        },
      },
    ]);
  };

  const getStatusBadgeColor = (status?: string) => {
    switch (status) {
      case 'ACCEPTED':
      case 'ARRIVING':
        return '#0284c7'; // blue
      case 'IN_PROGRESS':
        return '#16a34a'; // green
      case 'COMPLETED':
        return '#15803d'; // dark green
      case 'CANCELLED':
      case 'REJECTED':
        return '#dc2626'; // red
      default:
        return '#eab308'; // yellow/pending
    }
  };

  if (loading && !ride) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#16a34a" />
        <Text style={styles.loadingText}>Fetching Ride Status...</Text>
      </View>
    );
  }

  if (!ride) {
    return (
      <View style={styles.center}>
        <Text style={styles.errorText}>Ride not found</Text>
        <Button mode="contained" onPress={() => router.replace('/passenger')}>
          Return to Passenger Home
        </Button>
      </View>
    );
  }

  const isTerminal = ['COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED'].includes(ride.status);
  const driverComing = ['ACCEPTED', 'ARRIVING'].includes(ride.status);

  // Where the passenger is waiting: their GPS pin if they sent one, else the pickup landmark
  const waitPoint =
    ride.passengerLatitude && ride.passengerLongitude
      ? { latitude: ride.passengerLatitude, longitude: ride.passengerLongitude }
      : { latitude: ride.pickupLatitude, longitude: ride.pickupLongitude };
  const driverPos =
    ride.driverCurrentLatitude && ride.driverCurrentLongitude
      ? { latitude: ride.driverCurrentLatitude, longitude: ride.driverCurrentLongitude }
      : null;
  const driverDistance = driverPos ? haversineMeters(driverPos, waitPoint) : null;

  return (
    <ScrollView contentContainerStyle={styles.container}>
      {/* Status Banner */}
      <Card style={styles.statusCard}>
        <Card.Content>
          <View style={styles.statusRow}>
            <Text style={styles.statusHeading}>Ride Status</Text>
            <Badge style={[styles.badge, { backgroundColor: getStatusBadgeColor(ride.status) }]}>
              {ride.status.replace(/_/g, ' ')}
            </Badge>
          </View>
          <Text style={styles.statusSub}>
            {ride.status === 'REQUESTED' &&
              (ride.driverName
                ? `Waiting for ${ride.driverName} to accept…`
                : 'Looking for nearby rickshaws… the first driver to accept gets your ride.')}
            {ride.status === 'ACCEPTED' &&
              (driverDistance != null && driverDistance < 40
                ? 'Your rickshaw has arrived at the pickup point!'
                : `${ride.driverName || 'Driver'} accepted and is coming to pick you up.`)}
            {ride.status === 'ARRIVING' && 'Rickshaw is arriving at your pickup location now.'}
            {ride.status === 'IN_PROGRESS' && 'Trip in progress across JU campus.'}
            {ride.status === 'COMPLETED' && 'Trip successfully completed. Thank you!'}
            {ride.status === 'CANCELLED' && 'Ride request was cancelled.'}
            {ride.status === 'REJECTED' && 'The driver declined. Go back and choose another rickshaw or request any nearby one.'}
          </Text>
          {driverComing && driverDistance != null && (
            <Text style={styles.etaText}>
              🛺 {formatDistance(driverDistance)} away • ~{formatEta(driverDistance / 2.8)}
            </Text>
          )}
        </Card.Content>
      </Card>

      {/* Live Map */}
      <Text style={styles.sectionTitle}>Live Tracking</Text>
      <MapLeafletView
        routeCoordinates={routeResult?.coordinates}
        pickup={{
          id: ride.pickupLocationId,
          name: ride.pickupLocationName,
          bengaliName: '',
          category: 'Pickup',
          latitude: ride.pickupLatitude,
          longitude: ride.pickupLongitude,
          rickshawStand: false,
        }}
        destination={{
          id: ride.destinationLocationId,
          name: ride.destinationLocationName,
          bengaliName: '',
          category: 'Destination',
          latitude: ride.destinationLatitude,
          longitude: ride.destinationLongitude,
          rickshawStand: false,
        }}
        userLocation={gps && isOnCampus(gps) ? gps : null}
        fitKey={ride.status}
        driverLocation={
          ride.driverCurrentLatitude
            ? {
                latitude: ride.driverCurrentLatitude,
                longitude: ride.driverCurrentLongitude || ride.pickupLongitude,
                label: ride.rickshawNumber ? `Rickshaw ${ride.rickshawNumber}` : 'Rickshaw',
              }
            : null
        }
        height={220}
      />

      {/* Driver Information Card */}
      {ride.driverName ? (
        <Card style={styles.card}>
          <Card.Content>
            <Text style={styles.cardHeader}>🛺 Rickshaw & Driver Details</Text>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Rickshaw Number:</Text>
              <Text style={styles.infoVal}>{ride.rickshawNumber || 'JU Rickshaw'}</Text>
            </View>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Driver Name:</Text>
              <Text style={styles.infoVal}>{ride.driverName}</Text>
            </View>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Driver Phone:</Text>
              <Text style={styles.infoVal}>{ride.driverPhone || 'N/A'}</Text>
            </View>
            {ride.driverPhone && !isTerminal ? (
              <Button
                mode="contained-tonal"
                icon="phone"
                style={{ marginTop: 10, borderRadius: 8 }}
                onPress={() => Linking.openURL(`tel:${ride.driverPhone}`)}
              >
                Call driver
              </Button>
            ) : null}
            {ride.driverLocationSource && (
              <View style={styles.infoRow}>
                <Text style={styles.infoLabel}>GPS Source:</Text>
                <Text style={styles.infoVal}>{ride.driverLocationSource}</Text>
              </View>
            )}
          </Card.Content>
        </Card>
      ) : null}

      {/* Journey Summary */}
      <Card style={styles.card}>
        <Card.Content>
          <Text style={styles.cardHeader}>📍 Journey Details</Text>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Pickup:</Text>
            <Text style={styles.infoVal}>{ride.pickupLocationName}</Text>
          </View>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Destination:</Text>
            <Text style={styles.infoVal}>{ride.destinationLocationName}</Text>
          </View>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Seats Reserved:</Text>
            <Text style={styles.infoVal}>{ride.passengerCount} Seat(s)</Text>
          </View>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Estimated Fare:</Text>
            <Text style={[styles.infoVal, { color: '#16a34a', fontWeight: 'bold' }]}>৳ {ride.estimatedFareBdt} BDT</Text>
          </View>
        </Card.Content>
      </Card>

      {/* Action Buttons */}
      {!isTerminal && (
        <Button
          mode="outlined"
          textColor="#dc2626"
          style={styles.cancelBtn}
          loading={cancelling}
          disabled={cancelling}
          onPress={handleCancel}
        >
          Cancel Ride Request
        </Button>
      )}

      <Button
        mode="contained"
        style={styles.homeBtn}
        onPress={() => router.replace('/passenger')}
      >
        Return to Campus Home
      </Button>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    backgroundColor: '#f8fafc',
    paddingBottom: 40,
  },
  center: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
    backgroundColor: '#f8fafc',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 14,
    color: '#64748b',
  },
  errorText: {
    fontSize: 16,
    color: '#dc2626',
    marginBottom: 16,
  },
  statusCard: {
    backgroundColor: '#ffffff',
    borderRadius: 14,
    marginBottom: 14,
    elevation: 2,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  statusHeading: {
    fontSize: 17,
    fontWeight: 'bold',
    color: '#0f172a',
  },
  badge: {
    paddingHorizontal: 8,
    fontSize: 12,
  },
  etaText: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#0284c7',
    marginTop: 8,
  },
  statusSub: {
    fontSize: 13,
    color: '#475569',
    marginTop: 8,
    lineHeight: 18,
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#334155',
    marginBottom: 8,
    textTransform: 'uppercase',
  },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 14,
    marginTop: 14,
    elevation: 2,
  },
  cardHeader: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#0f172a',
    marginBottom: 10,
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 5,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  infoLabel: {
    fontSize: 13,
    color: '#64748b',
  },
  infoVal: {
    fontSize: 13,
    fontWeight: '600',
    color: '#0f172a',
  },
  cancelBtn: {
    borderColor: '#fca5a5',
    borderRadius: 8,
    marginTop: 18,
    paddingVertical: 4,
  },
  homeBtn: {
    backgroundColor: '#16a34a',
    borderRadius: 8,
    marginTop: 10,
    paddingVertical: 4,
  },
});
