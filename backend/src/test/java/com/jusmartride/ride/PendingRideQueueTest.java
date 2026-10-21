package com.jusmartride.ride;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pending Ride Queue (FIFO Dispatch) Tests")
class PendingRideQueueTest {

    private PendingRideQueue queue;

    @BeforeEach
    void setUp() {
        queue = new PendingRideQueue();
    }

    @Test
    @DisplayName("FIFO ordering is preserved for dispatch fairness")
    void testEnqueueAndDequeue_PreservesFifoOrder() {
        queue.enqueue("req_1");
        queue.enqueue("req_2");
        queue.enqueue("req_3");

        assertEquals(3, queue.size());
        assertEquals("req_1", queue.dequeue());
        assertEquals("req_2", queue.dequeue());
        assertEquals("req_3", queue.dequeue());
        assertTrue(queue.isEmpty());
        assertNull(queue.dequeue());
    }

    @Test
    @DisplayName("Request cancellation removes item from queue")
    void testRemove_CancelledRequest() {
        queue.enqueue("req_1");
        queue.enqueue("req_2");
        queue.enqueue("req_3");

        assertTrue(queue.remove("req_2"));
        assertEquals(2, queue.size());

        assertEquals("req_1", queue.dequeue());
        assertEquals("req_3", queue.dequeue());
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("Handles null and duplicate gracefully")
    void testEdgeCases() {
        queue.enqueue(null);
        queue.enqueue("");
        assertTrue(queue.isEmpty());

        assertFalse(queue.remove("non_existent"));
    }
}
