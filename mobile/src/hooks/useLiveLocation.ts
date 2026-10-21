import { useCallback, useEffect, useRef, useState } from 'react';
import * as Location from 'expo-location';
import { LatLng } from '../utils/geo';

export type PermissionState = 'checking' | 'granted' | 'denied' | 'services_off';

interface Options {
  enabled?: boolean;
  distanceInterval?: number; // meters between updates
  timeInterval?: number; // ms between updates (Android)
}

/**
 * Asks for foreground location permission and streams the phone's GPS position.
 */
export function useLiveLocation({ enabled = true, distanceInterval = 10, timeInterval = 5000 }: Options = {}) {
  const [permission, setPermission] = useState<PermissionState>('checking');
  const [location, setLocation] = useState<LatLng | null>(null);
  const [accuracy, setAccuracy] = useState<number | null>(null);
  const subscription = useRef<Location.LocationSubscription | null>(null);

  const start = useCallback(async () => {
    setPermission('checking');
    const servicesOn = await Location.hasServicesEnabledAsync();
    if (!servicesOn) {
      setPermission('services_off');
      return;
    }

    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') {
      setPermission('denied');
      return;
    }
    setPermission('granted');

    // Quick first fix from cache, then precise live updates
    const last = await Location.getLastKnownPositionAsync();
    if (last) {
      setLocation({ latitude: last.coords.latitude, longitude: last.coords.longitude });
    }

    subscription.current?.remove();
    subscription.current = await Location.watchPositionAsync(
      { accuracy: Location.Accuracy.High, distanceInterval, timeInterval },
      pos => {
        setLocation({ latitude: pos.coords.latitude, longitude: pos.coords.longitude });
        setAccuracy(pos.coords.accuracy ?? null);
      }
    );
  }, [distanceInterval, timeInterval]);

  useEffect(() => {
    if (!enabled) {
      subscription.current?.remove();
      subscription.current = null;
      return;
    }
    start().catch(() => setPermission('denied'));
    return () => {
      subscription.current?.remove();
      subscription.current = null;
    };
  }, [enabled, start]);

  return { permission, location, accuracy, retry: start };
}
