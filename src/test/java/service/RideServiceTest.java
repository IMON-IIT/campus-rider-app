package service;

import dsa.QueuePolicy;
import model.PassengerType;
import model.RideRequest;
import model.RideStatus;
import model.Rickshaw;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses the sample campus. Rickshaws start at:
 * 101 dairy_gate, 102 bottola, 103 shaheed_minar, 104 prantik_gate, 105 mmh_hall.
 */
class RideServiceTest {

    @TempDir
    Path dir;

    @Test
    void createRequestStoresRouteDistanceAndQueuesIt() throws Exception {
        RideService service = TestApp.start(dir);
        RideRequest ride = service.createRequest("Rafiq", PassengerType.STUDENT, "bottola", "dairy_gate");
        assertEquals(1, ride.getId());
        assertEquals(RideStatus.PENDING, ride.getStatus());
        double expected = service.getRouteService().findRoute("bottola", "dairy_gate").getTotalDistanceMeters();
        assertEquals(expected, ride.getDistanceMeters(), 1e-9);
        assertEquals(1, service.getPendingInOrder().size());
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        RideService service = TestApp.start(dir);
        assertThrows(IllegalArgumentException.class,
                () -> service.createRequest("  ", PassengerType.STUDENT, "bottola", "dairy_gate"));
        assertThrows(IllegalArgumentException.class,
                () -> service.createRequest("A", PassengerType.STUDENT, "bottola", "bottola"));
        assertThrows(IllegalArgumentException.class,
                () -> service.createRequest("A", PassengerType.STUDENT, "nowhere", "bottola"));
        assertTrue(service.getRides().isEmpty());
    }

    @Test
    void matchingPicksNearestAvailableRickshawAndNeverABusyOne() throws Exception {
        RideService service = TestApp.start(dir);
        service.createRequest("First", PassengerType.STUDENT, "bottola", "dairy_gate");
        service.createRequest("Second", PassengerType.STUDENT, "bottola", "dairy_gate");

        service.matchNextRequest();
        RideRequest first = service.getRide(1);
        assertEquals(RideStatus.MATCHED, first.getStatus());
        assertEquals("JU-RICK-102", first.getRickshawId()); // already standing at Bottola
        assertFalse(service.getRickshaw("JU-RICK-102").isAvailable());

        service.matchNextRequest();
        RideRequest second = service.getRide(2);
        assertEquals(RideStatus.MATCHED, second.getStatus());
        assertTrue(!second.getRickshawId().equals("JU-RICK-102"));
        assertTrue(service.getPendingInOrder().isEmpty());
    }

    @Test
    void noAvailableRickshawKeepsRequestInQueue() throws Exception {
        RideService service = TestApp.start(dir);
        for (int i = 1; i <= 6; i++) {
            service.createRequest("P" + i, PassengerType.STUDENT, "bottola", "dairy_gate");
        }
        for (int i = 1; i <= 5; i++) {
            service.matchNextRequest();
        }
        assertEquals(0, service.countAvailableRickshaws());
        assertThrows(IllegalStateException.class, service::matchNextRequest);
        assertEquals(RideStatus.PENDING, service.getRide(6).getStatus());
        assertEquals(1, service.getPendingInOrder().size());

        // When a ride finishes, its rickshaw is free again and the waiting request can be matched.
        service.startRide(1);
        service.completeRide(1);
        Rickshaw freed = service.getRickshaw(service.getRide(1).getRickshawId());
        assertTrue(freed.isAvailable());
        assertEquals("dairy_gate", freed.getLocationId());
        service.matchNextRequest();
        assertEquals(freed.getId(), service.getRide(6).getRickshawId());
    }

    @Test
    void priorityPolicyMatchesEmergencyFirst() throws Exception {
        RideService service = TestApp.start(dir);
        service.createRequest("Student", PassengerType.STUDENT, "bottola", "dairy_gate");
        service.createRequest("Emergency", PassengerType.EMERGENCY, "bottola", "medical_centre");
        service.setQueuePolicy(QueuePolicy.PRIORITY);
        service.matchNextRequest();
        assertEquals(RideStatus.MATCHED, service.getRide(2).getStatus());
        assertEquals(RideStatus.PENDING, service.getRide(1).getStatus());
    }

    @Test
    void invalidStateTransitionsAreRejected() throws Exception {
        RideService service = TestApp.start(dir);
        service.createRequest("A", PassengerType.STUDENT, "bottola", "dairy_gate");
        assertThrows(IllegalStateException.class, () -> service.startRide(1));     // PENDING -> IN_PROGRESS
        assertThrows(IllegalStateException.class, () -> service.completeRide(1));  // PENDING -> COMPLETED
        service.matchNextRequest();
        assertThrows(IllegalStateException.class, () -> service.completeRide(1));  // MATCHED -> COMPLETED
        service.startRide(1);
        assertThrows(IllegalStateException.class, () -> service.cancelRide(1));    // IN_PROGRESS -> CANCELLED
        service.completeRide(1);
        assertThrows(IllegalStateException.class, () -> service.completeRide(1));  // COMPLETED is final
        assertThrows(IllegalArgumentException.class, () -> service.startRide(99)); // unknown ride
        assertEquals(RideStatus.COMPLETED, service.getRide(1).getStatus());
    }

    @Test
    void cancellingFreesTheRickshawAndRemovesFromQueue() throws Exception {
        RideService service = TestApp.start(dir);
        service.createRequest("A", PassengerType.STUDENT, "bottola", "dairy_gate");
        service.createRequest("B", PassengerType.STUDENT, "bottola", "dairy_gate");
        service.matchNextRequest();
        service.cancelRide(1); // MATCHED -> CANCELLED
        assertTrue(service.getRickshaw("JU-RICK-102").isAvailable());
        service.cancelRide(2); // PENDING -> CANCELLED
        assertTrue(service.getPendingInOrder().isEmpty());
        assertThrows(IllegalStateException.class, service::matchNextRequest);
    }

    @Test
    void dataIsRestoredAfterRestart() throws Exception {
        RideService service = TestApp.start(dir);
        service.createRequest("Matched", PassengerType.STAFF, "bottola", "dairy_gate");
        service.createRequest("Waiting", PassengerType.STUDENT, "cse_building", "bottola");
        service.createRequest("Done", PassengerType.STUDENT, "dairy_gate", "bottola");
        service.matchNextRequest(); // ride 1 -> JU-RICK-102
        service.cancelRide(3);

        RideService restarted = TestApp.start(dir);
        assertEquals(3, restarted.getRides().size());
        assertEquals(RideStatus.MATCHED, restarted.getRide(1).getStatus());
        assertEquals("JU-RICK-102", restarted.getRide(1).getRickshawId());
        assertFalse(restarted.getRickshaw("JU-RICK-102").isAvailable());
        assertEquals(RideStatus.CANCELLED, restarted.getRide(3).getStatus());
        assertEquals(1, restarted.getPendingInOrder().size());
        assertEquals(2, restarted.getPendingInOrder().get(0).getId());
        // New ids continue after the highest saved id.
        assertEquals(4, restarted.createRequest("New", PassengerType.STUDENT, "bottola", "cse_building").getId());
    }

    @Test
    void commasInNamesDoNotBreakTheFile() throws Exception {
        RideService service = TestApp.start(dir);
        service.createRequest("Islam, Rafiqul", PassengerType.STUDENT, "bottola", "dairy_gate");
        RideService restarted = TestApp.start(dir);
        assertEquals(1, restarted.getRides().size());
        assertEquals("Islam  Rafiqul", restarted.getRide(1).getPassengerName());
    }
}
