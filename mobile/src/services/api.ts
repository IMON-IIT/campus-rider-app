import axios from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  AuthResponse,
  CampusLocation,
  RouteResult,
  CandidateRide,
  NearbyDriver,
  RideRequest,
  Vehicle,
  DriverTrip
} from '../types';

declare const process: { env: { [key: string]: string | undefined } };

const API_BASE = (typeof process !== 'undefined' && process.env?.EXPO_PUBLIC_API_URL) || 'http://127.0.0.1:8080';

export const api = axios.create({
  baseURL: `${API_BASE}/api/v1`,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
});

api.interceptors.request.use(async (config) => {
  try {
    const token = await AsyncStorage.getItem('@ju_jwt_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  } catch (e) {
    console.error('Failed to read token from storage', e);
  }
  return config;
});

// Authentication
export const authApi = {
  login: async (email: string, password: string): Promise<AuthResponse> => {
    const res = await api.post('/auth/login', { email, password });
    return res.data.data;
  },
  register: async (payload: {
    email: string;
    password: string;
    fullName: string;
    phone: string;
    role: string;
  }): Promise<AuthResponse> => {
    const res = await api.post('/auth/register', payload);
    return res.data.data;
  },
};

// Campus Locations & Road Network
export const campusApi = {
  getLocations: async (): Promise<CampusLocation[]> => {
    const res = await api.get('/campus/locations');
    return res.data.data;
  },
  searchLocations: async (query: string, limit = 8): Promise<CampusLocation[]> => {
    const res = await api.get('/campus/locations/search', {
      params: { q: query, limit },
    });
    return res.data.data;
  },
  getNearestLocation: async (lat: number, lng: number): Promise<CampusLocation> => {
    const res = await api.get('/campus/locations/nearest', { params: { lat, lng } });
    return res.data.data;
  },
  getGraph: async () => {
    const res = await api.get('/campus/graph');
    return res.data.data;
  },
};

// Dijkstra Routing & Ride Matching
export const routeApi = {
  getShortestPath: async (sourceId: string, targetId: string): Promise<RouteResult> => {
    const res = await api.post('/routes/shortest-path', { sourceId, targetId });
    return res.data.data;
  },
  getRideOptions: async (
    pickupId: string,
    destinationId: string,
    passengerCount = 1
  ): Promise<CandidateRide[]> => {
    const res = await api.post('/routes/ride-options', {
      pickupId,
      destinationId,
      passengerCount,
    });
    return res.data.data;
  },
};

// Driver discovery (passenger side)
export const nearbyApi = {
  getNearbyDrivers: async (lat: number, lng: number, radius = 3000): Promise<NearbyDriver[]> => {
    const res = await api.get('/drivers/nearby', { params: { lat, lng, radius } });
    return res.data.data;
  },
};

// Passenger Ride Requests
export const rideApi = {
  createRideRequest: async (payload: {
    pickupLocationId: string;
    destinationLocationId: string;
    passengerCount: number;
    selectedVehicleId?: string;
    passengerLatitude?: number;
    passengerLongitude?: number;
  }): Promise<RideRequest> => {
    const res = await api.post('/rides/requests', payload);
    return res.data.data;
  },
  getMyRequests: async (): Promise<RideRequest[]> => {
    const res = await api.get('/rides/requests/me');
    return res.data.data;
  },
  getRequestById: async (id: string): Promise<RideRequest> => {
    const res = await api.get(`/rides/requests/${id}`);
    return res.data.data;
  },
  cancelRequest: async (id: string): Promise<RideRequest> => {
    const res = await api.post(`/rides/requests/${id}/cancel`);
    return res.data.data;
  },
};

// Driver Operations
export const driverApi = {
  getDriverStatus: async (): Promise<{ vehicle?: Vehicle; activeTrip?: DriverTrip; activeRequests?: RideRequest[] }> => {
    const res = await api.get('/driver/me');
    return res.data.data;
  },
  setAvailability: async (isOnline: boolean, currentLocationId?: string): Promise<Vehicle> => {
    const res = await api.post('/driver/availability', { isOnline, currentLocationId });
    return res.data.data;
  },
  getIncomingRequests: async (): Promise<RideRequest[]> => {
    const res = await api.get('/driver/requests');
    return res.data.data;
  },
  acceptRequest: async (requestId: string): Promise<RideRequest> => {
    const res = await api.post(`/driver/requests/${requestId}/accept`);
    return res.data.data;
  },
  rejectRequest: async (requestId: string, reason = 'Driver unavailable'): Promise<RideRequest> => {
    const res = await api.post(`/driver/requests/${requestId}/reject`, { reason });
    return res.data.data;
  },
  updateLocation: async (payload: {
    driverId?: string;
    vehicleId?: string;
    latitude: number;
    longitude: number;
    nearestLocationId?: string;
    locationSource?: string;
    timestamp?: number;
  }) => {
    const res = await api.post('/driver/location', {
      ...payload,
      timestamp: payload.timestamp || Date.now(),
      locationSource: payload.locationSource || 'LIVE_PHONE',
    });
    return res.data.data;
  },
  updateTripStatus: async (tripId: string, status: string, currentLocationId?: string) => {
    const res = await api.post(`/driver/trips/${tripId}/status`, {
      status,
      currentLocationId,
    });
    return res.data.data;
  },
};
