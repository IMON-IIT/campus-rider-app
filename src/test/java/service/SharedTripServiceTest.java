package service;

import model.RideRequest;
import model.RideStatus;
import model.Rickshaw;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import storage.CsvStorage;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedTripServiceTest {
    @TempDir
    Path dir;

    @Test
    void onlyOnlineActiveTripsWithCompatibleRoutesAndSeatsAreOffered() throws Exception {
        RideService service = TestApp.start(dir);
        service.startTrip("JU-RICK-101", "dairy_gate", "rafiq_jabbar_hall", 4, 4);

        List<RideMatchingService.TripMatch> options =
                service.findAvailableTrips("shaheed_minar", "rafiq_jabbar_hall", 2);
        assertTrue(options.stream().anyMatch(match -> match.getRickshaw().getId().equals("JU-RICK-101")));
        assertTrue(options.get(0).getExtraDistanceMeters() <= 150.0);

        service.setDriverOnline("JU-RICK-101", false);
        assertTrue(service.findAvailableTrips("shaheed_minar", "rafiq_jabbar_hall", 1).stream()
                .noneMatch(match -> match.getRickshaw().getId().equals("JU-RICK-101")));
    }

    @Test
    void tripCanBeStartedAndEndedAndCandidatesAreRankedDeterministically() throws Exception {
        RideService service = TestApp.start(dir);
        service.startTrip("JU-RICK-102", "dairy_gate", "rafiq_jabbar_hall", 4, 4);
        service.startTrip("JU-RICK-101", "dairy_gate", "rafiq_jabbar_hall", 4, 4);

        List<RideMatchingService.TripMatch> options =
                service.findAvailableTrips("shaheed_minar", "rafiq_jabbar_hall", 1);
        assertEquals("JU-RICK-101", options.get(0).getRickshaw().getId());
        int pickup = indexOf(options.get(0), "shaheed_minar", model.TripStop.Type.PICKUP);
        int dropoff = indexOf(options.get(0), "rafiq_jabbar_hall", model.TripStop.Type.DROPOFF);
        assertTrue(pickup < dropoff);

        service.endTrip("JU-RICK-101");
        assertFalse(service.getRickshaw("JU-RICK-101").hasActiveTrip());
        assertTrue(service.findAvailableTrips("shaheed_minar", "rafiq_jabbar_hall", 1).stream()
                .noneMatch(match -> match.getRickshaw().getId().equals("JU-RICK-101")));
    }

    @Test
    void rejectsRoutesThatExceedTheDetourLimit() throws Exception {
        RideService service = TestApp.start(dir);
        service.startTrip("JU-RICK-101", "dairy_gate", "shaheed_minar", 4, 4);

        assertTrue(service.findAvailableTrips("bottola", "central_library", 1).stream()
                .noneMatch(match -> match.getRickshaw().getId().equals("JU-RICK-101")));
    }

    @Test
    void acceptsMultipleBookingsWithoutOverbookingAndRestoresSeatsOnCancellation() throws Exception {
        RideService service = TestApp.start(dir);
        service.startTrip("JU-RICK-101", "dairy_gate", "rafiq_jabbar_hall", 4, 4);

        RideRequest first = service.bookSharedRide("Passenger A", "01700000001",
                "shaheed_minar", "rafiq_jabbar_hall", 2, "JU-RICK-101");
        RideRequest second = service.bookSharedRide("Passenger B", "01700000002",
                "shaheed_minar", "rafiq_jabbar_hall", 1, "JU-RICK-101");
        assertEquals(4, service.getRickshaw("JU-RICK-101").getAvailableSeats());

        service.acceptRideRequest(first.getId(), "JU-RICK-101");
        assertEquals(2, service.getRickshaw("JU-RICK-101").getAvailableSeats());
        service.acceptRideRequest(second.getId(), "JU-RICK-101");
        assertEquals(1, service.getRickshaw("JU-RICK-101").getAvailableSeats());
        assertTrue(service.findAvailableTrips("shaheed_minar", "rafiq_jabbar_hall", 2).stream()
                .noneMatch(match -> match.getRickshaw().getId().equals("JU-RICK-101")));

        assertThrows(IllegalStateException.class,
                () -> service.bookSharedRide("Passenger C", "01700000003",
                        "shaheed_minar", "rafiq_jabbar_hall", 2, "JU-RICK-101"));
        assertThrows(IllegalStateException.class,
                () -> service.bookSharedRide("Passenger A", "01700000001",
                        "shaheed_minar", "rafiq_jabbar_hall", 1, "JU-RICK-101"));
        service.cancelRide(second.getId());
        assertEquals(2, service.getRickshaw("JU-RICK-101").getAvailableSeats());
    }

    @Test
    void seatSummarySeparatesOfferedAcceptedAndOccupiedSeatsAndReleasesCancelledSeats() throws Exception {
        RideService service = TestApp.start(dir);
        service.startTrip("JU-RICK-101", "dairy_gate", "rafiq_jabbar_hall", 4, 4);

        RideRequest first = service.bookSharedRide("Passenger A", "01700000001",
                "shaheed_minar", "rafiq_jabbar_hall", 1, "JU-RICK-101");
        RideService.SeatAvailability offered = service.getSeatAvailability("JU-RICK-101");
        assertEquals(4, offered.totalSeats());
        assertEquals(1, offered.bookedSeats());
        assertEquals(0, offered.occupiedSeats());
        assertEquals(3, offered.availableSeats());
        assertEquals(1, offered.passengerBookings());

        service.acceptRideRequest(first.getId(), "JU-RICK-101");
        RideRequest second = service.bookSharedRide("Passenger B", "01700000002",
                "shaheed_minar", "rafiq_jabbar_hall", 2, "JU-RICK-101");
        RideService.SeatAvailability twoBookings = service.getSeatAvailability("JU-RICK-101");
        assertEquals(3, twoBookings.bookedSeats());
        assertEquals(0, twoBookings.occupiedSeats());
        assertEquals(1, twoBookings.availableSeats());
        assertEquals(2, twoBookings.passengerBookings());

        service.acceptRideRequest(second.getId(), "JU-RICK-101");
        assertEquals(1, service.getSeatAvailability("JU-RICK-101").availableSeats());
        assertThrows(IllegalStateException.class, () -> service.bookSharedRide("Passenger C",
                "01700000003", "shaheed_minar", "rafiq_jabbar_hall", 2, "JU-RICK-101"));

        service.cancelRide(second.getId());
        RideService.SeatAvailability afterCancel = service.getSeatAvailability("JU-RICK-101");
        assertEquals(1, afterCancel.bookedSeats());
        assertEquals(3, afterCancel.availableSeats());
        assertEquals(0, afterCancel.occupiedSeats());
    }

    @Test
    void passengerCannotHaveActiveBookingsOnMultipleTripsButCanBookAgainAfterCancellation() throws Exception {
        RideService service = TestApp.start(dir);
        service.startTrip("JU-RICK-101", "dairy_gate", "rafiq_jabbar_hall", 3, 3);
        service.startTrip("JU-RICK-102", "dairy_gate", "rafiq_jabbar_hall", 3, 3);

        RideRequest first = service.bookSharedRide("Passenger", "01700000001",
                "shaheed_minar", "rafiq_jabbar_hall", 1, "JU-RICK-101");
        assertThrows(IllegalStateException.class, () -> service.bookSharedRide("Passenger", "01700000001",
                "shaheed_minar", "rafiq_jabbar_hall", 1, "JU-RICK-102"));

        service.cancelRide(first.getId());
        RideRequest next = service.bookSharedRide("Passenger", "01700000001",
                "shaheed_minar", "rafiq_jabbar_hall", 1, "JU-RICK-102");
        assertEquals(RideStatus.OFFERED, next.getStatus());
        assertEquals("JU-RICK-102", next.getRickshawId());
    }

    @Test
    void rejectionFallsBackToAnotherTripAndNeverRetriesTheRejectedRickshaw() throws Exception {
        RideService service = TestApp.start(dir);
        service.startTrip("JU-RICK-101", "dairy_gate", "rafiq_jabbar_hall", 3, 3);
        service.startTrip("JU-RICK-102", "dairy_gate", "rafiq_jabbar_hall", 3, 3);

        RideRequest ride = service.bookSharedRide("Passenger", "01700000001",
                "shaheed_minar", "rafiq_jabbar_hall", 1, "JU-RICK-101");
        service.rejectRideRequest(ride.getId(), "JU-RICK-101");

        assertEquals(RideStatus.OFFERED, ride.getStatus());
        assertEquals("JU-RICK-102", ride.getRickshawId());
        assertTrue(ride.getRejectedRickshawIds().contains("JU-RICK-101"));
        assertFalse(service.getAssignedRequests("JU-RICK-101").contains(ride));
        assertEquals(3, service.getRickshaw("JU-RICK-101").getAvailableSeats());

        service.rejectRideRequest(ride.getId(), "JU-RICK-102");
        assertEquals(RideStatus.NO_DRIVER_AVAILABLE, ride.getStatus());
        assertTrue(ride.getRejectedRickshawIds().contains("JU-RICK-102"));
    }

    @Test
    void passengerBookingIsSavedAndDeliveredToTheAssignedDriverAfterReload() throws Exception {
        RideService service = TestApp.start(dir);
        service.startTrip("JU-RICK-101", "dairy_gate", "rafiq_jabbar_hall", 3, 3);

        RideMatchingService.TripMatch selected = service.findAvailableTrips(
                        "shaheed_minar", "rafiq_jabbar_hall", 1).stream()
                .filter(match -> match.getRickshaw().getId().equals("JU-RICK-101"))
                .findFirst().orElseThrow();
        RideRequest request = service.bookSharedRide("Passenger", "01700000001",
                "shaheed_minar", "rafiq_jabbar_hall", 1, selected.getRickshaw().getId());

        assertEquals(RideStatus.OFFERED, request.getStatus());
        assertEquals("JU-RICK-101", request.getRickshawId());
        assertEquals(List.of(request), service.getAssignedRequests("JU-RICK-101"));
        assertTrue(service.getAssignedRequests("JU-RICK-102").isEmpty());

        RideRequest persisted = new CsvStorage(dir).loadRides().stream()
                .filter(ride -> ride.getId() == request.getId())
                .findFirst().orElseThrow();
        assertEquals(RideStatus.OFFERED, persisted.getStatus());
        assertEquals("JU-RICK-101", persisted.getRickshawId());

        RideService restarted = TestApp.start(dir);
        List<RideRequest> reloadedOffers = restarted.getAssignedRequests("JU-RICK-101");
        assertEquals(1, reloadedOffers.size());
        assertEquals(request.getId(), reloadedOffers.get(0).getId());
        assertEquals(RideStatus.OFFERED, reloadedOffers.get(0).getStatus());
        assertTrue(restarted.getAssignedRequests("JU-RICK-102").isEmpty());
    }

    @Test
    void tripMovementWaitsForAcceptanceAndDoesNotStartAfterRejection() throws Exception {
        RideService service = TestApp.start(dir);
        service.startTrip("JU-RICK-101", "dairy_gate", "rafiq_jabbar_hall", 3, 3);
        service.startTrip("JU-RICK-102", "dairy_gate", "rafiq_jabbar_hall", 3, 3);
        RideRequest ride = service.bookSharedRide("Passenger", "01700000001",
                "shaheed_minar", "central_library", 1, "JU-RICK-101");

        assertEquals("dairy_gate", service.advanceTripOneStep("JU-RICK-101"));
        service.rejectRideRequest(ride.getId(), "JU-RICK-101");
        assertEquals("dairy_gate", service.advanceTripOneStep("JU-RICK-101"));
        assertEquals("dairy_gate", service.advanceTripOneStep("JU-RICK-102"));

        service.acceptRideRequest(ride.getId(), "JU-RICK-102");
        assertEquals("shaheed_minar", service.advanceTripOneStep("JU-RICK-102"));
    }

    @Test
    void noRouteCompatibleTripReportsNoDriverAvailable() throws Exception {
        RideService service = TestApp.start(dir);
        service.startTrip("JU-RICK-101", "dairy_gate", "shaheed_minar", 1, 0);

        RideRequest ride = service.bookSharedRide("Passenger", "01700000001",
                "bottola", "central_library", 1, null);
        assertEquals(RideStatus.NO_DRIVER_AVAILABLE, ride.getStatus());
    }

    @Test
    void tripSimulationMovesToPickupWaitsForOnboardThenCompletesAndPersists() throws Exception {
        RideService service = TestApp.start(dir);
        service.startTrip("JU-RICK-101", "dairy_gate", "central_library", 3, 3);
        assertEquals("dairy_gate", service.advanceTripOneStep("JU-RICK-101"));
        assertNull(service.getTripMovement("JU-RICK-101"),
                "A trip without an accepted passenger must not produce movement.");
        RideRequest ride = service.bookSharedRide("Passenger", "01700000001",
                "shaheed_minar", "central_library", 1, "JU-RICK-101");
        assertEquals("dairy_gate", service.advanceTripOneStep("JU-RICK-101"));
        service.acceptRideRequest(ride.getId(), "JU-RICK-101");

        assertEquals("shaheed_minar", service.advanceTripOneStep("JU-RICK-101"));
        RideService.TripMovement movement = service.getTripMovement("JU-RICK-101");
        assertEquals("dairy_gate", movement.fromLocationId());
        assertEquals("shaheed_minar", movement.toLocationId());
        assertEquals(RideStatus.ARRIVED_AT_PICKUP, ride.getStatus());
        assertEquals("shaheed_minar", service.advanceTripOneStep("JU-RICK-101"));

        service.confirmPassengerPickup(ride.getId(), "JU-RICK-101");
        assertEquals(1, service.getSeatAvailability("JU-RICK-101").occupiedSeats());
        for (int step = 0; step < 20 && ride.getStatus() != RideStatus.ARRIVED_AT_DESTINATION; step++) {
            service.advanceTripOneStep("JU-RICK-101");
        }
        assertEquals(RideStatus.ARRIVED_AT_DESTINATION, ride.getStatus());
        assertTrue(service.getActivePassengerRides("JU-RICK-101").contains(ride),
                "The driver must still see a booking while it awaits completion confirmation.");
        assertEquals("central_library", service.getRickshaw("JU-RICK-101").getLocationId());
        assertEquals("central_library", service.advanceTripOneStep("JU-RICK-101"),
                "The simulator must wait for the driver to confirm completion at the destination.");
        service.completePassengerTrip(ride.getId(), "JU-RICK-101");
        assertEquals(RideStatus.COMPLETED, ride.getStatus());
        assertEquals(3, service.getRickshaw("JU-RICK-101").getAvailableSeats());
        String completedPosition = service.getRickshaw("JU-RICK-101").getLocationId();
        assertEquals(completedPosition, service.advanceTripOneStep("JU-RICK-101"));

        RideService restarted = TestApp.start(dir);
        assertEquals(RideStatus.COMPLETED, restarted.getRide(ride.getId()).getStatus());
        assertEquals(3, restarted.getRickshaw("JU-RICK-101").getAvailableSeats());
    }

    @Test
    void coLocatedWaitingPickupDoesNotBlockCurrentPassengerDropoff() throws Exception {
        RideService service = TestApp.start(dir);
        service.startTrip("JU-RICK-101", "dairy_gate", "rafiq_jabbar_hall", 4, 4);
        RideRequest first = service.bookSharedRide("Passenger A", "01700000001",
                "shaheed_minar", "central_library", 1, "JU-RICK-101");
        service.acceptRideRequest(first.getId(), "JU-RICK-101");
        for (int step = 0; step < 20 && first.getStatus() != RideStatus.ARRIVED_AT_PICKUP; step++) {
            service.advanceTripOneStep("JU-RICK-101");
        }
        assertEquals(RideStatus.ARRIVED_AT_PICKUP, first.getStatus());
        service.confirmPassengerPickup(first.getId(), "JU-RICK-101");

        RideRequest second = service.bookSharedRide("Passenger B", "01700000002",
                "central_library", "rafiq_jabbar_hall", 1, "JU-RICK-101");
        service.acceptRideRequest(second.getId(), "JU-RICK-101");
        for (int step = 0; step < 20 && second.getStatus() != RideStatus.ARRIVED_AT_PICKUP; step++) {
            service.advanceTripOneStep("JU-RICK-101");
        }
        assertEquals(RideStatus.ARRIVED_AT_PICKUP, second.getStatus());
        assertEquals(RideStatus.IN_PROGRESS, first.getStatus());
        assertEquals("central_library", service.getRickshaw("JU-RICK-101").getLocationId());

        assertEquals("central_library", service.advanceTripOneStep("JU-RICK-101"));
        assertEquals(RideStatus.ARRIVED_AT_DESTINATION, first.getStatus());
        assertEquals("central_library", service.getRickshaw("JU-RICK-101").getLocationId());
        service.completePassengerTrip(first.getId(), "JU-RICK-101");
        service.confirmPassengerPickup(second.getId(), "JU-RICK-101");

        for (int step = 0; step < 20 && second.getStatus() != RideStatus.ARRIVED_AT_DESTINATION; step++) {
            service.advanceTripOneStep("JU-RICK-101");
        }
        assertEquals(RideStatus.ARRIVED_AT_DESTINATION, second.getStatus());
    }

    @Test
    void startedTripAndUnacceptedSeatOffersPersistAcrossRestart() throws Exception {
        RideService service = TestApp.start(dir);
        service.startTrip("JU-RICK-101", "dairy_gate", "rafiq_jabbar_hall", 4, 4);
        RideRequest ride = service.bookSharedRide("Passenger", "01700000001",
                "shaheed_minar", "rafiq_jabbar_hall", 2, "JU-RICK-101");

        RideService restarted = TestApp.start(dir);
        Rickshaw restored = restarted.getRickshaw("JU-RICK-101");
        assertTrue(restored.hasActiveTrip());
        assertEquals("rafiq_jabbar_hall", restored.getDestinationId());
        assertEquals(4, restored.getAvailableSeats());
        assertEquals(RideStatus.OFFERED, restarted.getRide(ride.getId()).getStatus());
        assertTrue(restarted.findAvailableTrips("shaheed_minar", "rafiq_jabbar_hall", 3).stream()
                .noneMatch(match -> match.getRickshaw().getId().equals("JU-RICK-101")));
    }

    @Test
    void acceptedSeatCountAndItineraryPersistAcrossRestart() throws Exception {
        RideService service = TestApp.start(dir);
        service.startTrip("JU-RICK-101", "dairy_gate", "rafiq_jabbar_hall", 4, 4);
        RideRequest ride = service.bookSharedRide("Passenger", "01700000001",
                "shaheed_minar", "central_library", 2, "JU-RICK-101");
        service.acceptRideRequest(ride.getId(), "JU-RICK-101");

        RideService restarted = TestApp.start(dir);
        Rickshaw restored = restarted.getRickshaw("JU-RICK-101");
        assertEquals(2, restored.getAvailableSeats());
        assertEquals(RideStatus.MATCHED, restarted.getRide(ride.getId()).getStatus());
        assertTrue(restored.getItinerary().stream().anyMatch(stop -> stop.rideId() == ride.getId()));
    }

    private static int indexOf(RideMatchingService.TripMatch match, String locationId,
                               model.TripStop.Type type) {
        for (int i = 0; i < match.getItinerary().size(); i++) {
            model.TripStop stop = match.getItinerary().get(i);
            if (stop.locationId().equals(locationId) && stop.type() == type) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }
}
