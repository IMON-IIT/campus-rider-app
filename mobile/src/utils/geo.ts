import { CampusLocation } from '../types';

export interface LatLng {
  latitude: number;
  longitude: number;
}

// Shaheed Minar area, roughly the middle of JU campus
export const CAMPUS_CENTER: LatLng = { latitude: 23.8812, longitude: 90.2673 };

// Anyone farther than this from the campus center is treated as "off campus"
export const CAMPUS_RADIUS_METERS = 2500;

export function haversineMeters(a: LatLng, b: LatLng): number {
  const R = 6371000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(b.latitude - a.latitude);
  const dLng = toRad(b.longitude - a.longitude);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(a.latitude)) * Math.cos(toRad(b.latitude)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

export function isOnCampus(point: LatLng): boolean {
  return haversineMeters(point, CAMPUS_CENTER) <= CAMPUS_RADIUS_METERS;
}

export function nearestLocation(point: LatLng, locations: CampusLocation[]): CampusLocation | null {
  let best: CampusLocation | null = null;
  let bestDist = Infinity;
  for (const loc of locations) {
    const d = haversineMeters(point, loc);
    if (d < bestDist) {
      bestDist = d;
      best = loc;
    }
  }
  return best;
}

export function formatDistance(meters: number): string {
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1)} km`;
}

export function formatEta(seconds: number): string {
  return `${Math.max(1, Math.round(seconds / 60))} min`;
}

// Same rule as the backend (GeoUtils.estimateFareBdt): ৳5 per 250m, minimum ৳15
export function estimateFareBdt(distanceMeters: number): number {
  return Math.max(15, Math.round(distanceMeters * 0.02) * 5);
}
