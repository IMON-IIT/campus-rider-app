package dsa;

import model.PassengerType;
import model.RideRequest;
import model.RideStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class RideQueueTest {

    private static RideRequest request(int id, PassengerType type) {
        return new RideRequest(id, "P" + id, type, "A", "B", 10, RideStatus.PENDING, "");
    }

    private static List<Integer> pollAll(RideQueue queue) {
        List<Integer> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            order.add(queue.poll().getId());
        }
        return order;
    }

    private static RideQueue filled(QueuePolicy policy) {
        RideQueue queue = new RideQueue(policy);
        queue.add(request(1, PassengerType.STUDENT));
        queue.add(request(2, PassengerType.STAFF));
        queue.add(request(3, PassengerType.STUDENT));
        queue.add(request(4, PassengerType.EMERGENCY));
        queue.add(request(5, PassengerType.STAFF));
        return queue;
    }

    @Test
    void fifoKeepsArrivalOrder() {
        assertEquals(List.of(1, 2, 3, 4, 5), pollAll(filled(QueuePolicy.FIFO)));
    }

    @Test
    void priorityServesEmergencyThenStaffThenStudentsEarliestFirst() {
        assertEquals(List.of(4, 2, 5, 1, 3), pollAll(filled(QueuePolicy.PRIORITY)));
    }

    @Test
    void processingOrderPreviewDoesNotChangeTheQueue() {
        RideQueue queue = filled(QueuePolicy.PRIORITY);
        List<Integer> preview = queue.inProcessingOrder().stream().map(RideRequest::getId).collect(Collectors.toList());
        assertEquals(List.of(4, 2, 5, 1, 3), preview);
        assertEquals(5, queue.size());
    }

    @Test
    void switchingPolicyKeepsAllRequests() {
        RideQueue queue = filled(QueuePolicy.PRIORITY);
        queue.setPolicy(QueuePolicy.FIFO);
        assertEquals(List.of(1, 2, 3, 4, 5), pollAll(queue));
    }

    @Test
    void sameRequestIsNeverQueuedTwiceAndRemovedRequestsAreGone() {
        RideQueue queue = new RideQueue(QueuePolicy.FIFO);
        RideRequest r = request(1, PassengerType.STUDENT);
        queue.add(r);
        assertFalse(queue.add(r));
        assertEquals(1, queue.size());
        queue.remove(r);
        assertNull(queue.poll());
    }
}
