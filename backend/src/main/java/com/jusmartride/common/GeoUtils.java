package com.jusmartride.common;

/**
 * Great-circle distance helpers for raw GPS coordinates.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoUtils() {
    }

    /**
     * Haversine distance between two latitude/longitude points.
     *
     * @return distance in meters
     */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }

    /**
     * Campus fare rule shared with the mobile client: ৳5 per 250m, minimum ৳15.
     */
    public static double estimateFareBdt(double distanceMeters) {
        return Math.max(15, Math.round(distanceMeters * 0.02) * 5);
    }
}
