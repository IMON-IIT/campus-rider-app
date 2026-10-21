package com.jusmartride.ride;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe FIFO Queue for dispatching unassigned campus ride requests.
 *
 * Design Rationale:
 * A FIFO (First-In, First-Out) Queue enforces fairness in ride dispatching.
 * When multiple passengers request rides from busy pickup points (e.g. Dairy Gate during rush hour),
 * requests must be matched and offered to incoming drivers in the exact order they were submitted,
 * preventing starvation and arbitrary dispatch favoritism.
 *
 * Backed by ConcurrentLinkedQueue for lock-free multi-threaded operations,
 * paired with a ConcurrentHashMap for O(1) request cancellation/lookup.
 */
public class PendingRideQueue {

    private final Queue<String> queue = new ConcurrentLinkedQueue<>();
    private final Map<String, Long> requestTimestamps = new ConcurrentHashMap<>();

    public PendingRideQueue() {
    }

    /**
     * Enqueues a pending ride request.
     *
     * @param requestId unique identifier of the ride request
     */
    public void enqueue(String requestId) {
        if (requestId == null || requestId.isBlank()) return;
        requestTimestamps.put(requestId, System.currentTimeMillis());
        queue.offer(requestId);
    }

    /**
     * Polls the next oldest pending ride request in FIFO order.
     *
     * @return next requestId or null if empty
     */
    public String dequeue() {
        while (!queue.isEmpty()) {
            String next = queue.poll();
            if (next != null && requestTimestamps.containsKey(next)) {
                requestTimestamps.remove(next);
                return next;
            }
        }
        return null;
    }

    /**
     * Removes a cancelled or expired request from the queue.
     */
    public boolean remove(String requestId) {
        if (requestId == null) return false;
        return requestTimestamps.remove(requestId) != null;
    }

    public boolean contains(String requestId) {
        return requestId != null && requestTimestamps.containsKey(requestId);
    }

    public int size() {
        return requestTimestamps.size();
    }

    public boolean isEmpty() {
        return requestTimestamps.isEmpty();
    }

    public void clear() {
        queue.clear();
        requestTimestamps.clear();
    }
}
