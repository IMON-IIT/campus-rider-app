package service;

import dsa.RouteResult;

import java.util.OptionalLong;

/** Calculates demonstration fares from a shortest-path route distance. */
public final class EstimatedFareCalculator {
    public static final long RATE_BDT_PER_KILOMETER = 10;

    private EstimatedFareCalculator() {
    }

    public static OptionalLong estimateFare(RouteResult route) {
        if (route == null || !route.isFound()) {
            return OptionalLong.empty();
        }
        double distanceMeters = route.getTotalDistanceMeters();
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.round(distanceMeters * RATE_BDT_PER_KILOMETER / 1000));
    }
}
