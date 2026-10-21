export type Role = 'ROLE_PASSENGER' | 'ROLE_DRIVER' | 'ROLE_ADMIN';

export type RideStatus =
  | 'REQUESTED'
  | 'ACCEPTED'
  | 'ARRIVING'
  | 'PICKUP_REACHED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REJECTED'
  | 'EXPIRED';

export interface User {
  id: string;
  email: string;
  fullName: string;
  phone: string;
  role: Role;
}

export interface AuthResponse {
  token: string;
  type?: string;
  userId?: string;
  id?: string;
  email: string;
  fullName: string;
  phone: string;
  role: Role;
  vehicleId?: string;
  rickshawNumber?: string;
  capacity?: number;
  isOnline?: boolean;
}

export interface CampusLocation {
  id: string;
  name: string;
  bengaliName: string;
  category: string;
  latitude: number;
  longitude: number;
  rickshawStand: boolean;
  description?: string;
}

export interface RouteSegment {
  sourceId: string;
  targetId: string;
  distanceMeters: number;
  roadName: string;
}

export interface RouteResult {
  sourceId: string;
  targetId: string;
  totalDistanceMeters: number;
  estimatedTravelTimeSeconds: number;
  pathNodeIds: string[];
  pathVertices?: { id: string; name: string; latitude: number; longitude: number }[];
  coordinates?: [number, number][]; // [lat, lng]
  reachable: boolean;
}

// Mirrors backend matching.CandidateRide
export interface CandidateRide {
  tripId: string | null;
  vehicleId: string;
  driverId: string;
  driverName: string;
  rickshawNumber: string;
  totalCapacity: number;
  occupiedSeats: number;
  availableSeats: number;
  sharedRide: boolean;
  driverCurrentLocationId: string;
  pickupDistanceMeters: number;
  estimatedPickupWaitSeconds: number;
  detourDistanceMeters: number;
  detourRatio: number;
  compositeScore: number;
  recommended: boolean;
  recommendationReason: string | null;
}

export interface NearbyDriver {
  driverId: string;
  vehicleId: string;
  driverName: string;
  driverPhone: string;
  rickshawNumber: string;
  latitude: number;
  longitude: number;
  distanceMeters: number;
  etaSeconds: number;
  capacity: number;
  occupiedSeats: number;
  availableSeats: number;
  onTrip: boolean;
  nearestLocationId?: string;
  nearestLocationName?: string;
  locationSource: string;
  lastUpdateSecondsAgo?: number;
}

export interface RideRequest {
  id: string;
  passengerId: string;
  passengerName: string;
  passengerPhone: string;
  driverId?: string;
  driverName?: string;
  driverPhone?: string;
  vehicleId?: string;
  rickshawNumber?: string;
  pickupLocationId: string;
  pickupLocationName: string;
  pickupLatitude: number;
  pickupLongitude: number;
  destinationLocationId: string;
  destinationLocationName: string;
  destinationLatitude: number;
  destinationLongitude: number;
  passengerCount: number;
  status: RideStatus;
  requestedAt: string;
  acceptedAt?: string;
  completedAt?: string;
  cancelledAt?: string;
  estimatedFareBdt: number;
  detourRatio: number;
  rejectionReason?: string;
  driverCurrentLatitude?: number;
  driverCurrentLongitude?: number;
  driverLocationSource?: string;
  driverLocationUpdatedAt?: number;
  passengerLatitude?: number;
  passengerLongitude?: number;
  driverToPickupMeters?: number;
}

export interface Vehicle {
  id: string;
  driver: User;
  rickshawNumber: string;
  capacity: number;
  isOnline: boolean;
  currentLocation?: CampusLocation;
  currentLatitude?: number;
  currentLongitude?: number;
  lastLocationUpdate?: string;
  locationSource: string;
}

export interface DriverTrip {
  id: string;
  vehicleId: string;
  driverId: string;
  status: string;
  currentLocationId?: string;
  occupiedSeats: number;
  totalDistanceMeters: number;
}
