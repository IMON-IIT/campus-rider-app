package dsa;

import model.RideRequest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Holds pending ride requests in one of two queues:
 *
 * FIFO:     ArrayDeque     - offer and poll in O(1).
 * PRIORITY: PriorityQueue  - binary min-heap, offer and poll in O(log n).
 *
 * Both implement java.util.Queue, so the rest of the code does not care
 * which one is active.
 */
public class RideQueue {

    /** Lower priority number first; for equal priority the earlier request (smaller id) first. */
    public static final Comparator<RideRequest> PRIORITY_ORDER =
            Comparator.comparingInt((RideRequest r) -> r.getPassengerType().getPriority())
                    .thenComparingInt(RideRequest::getId);

    private QueuePolicy policy;
    private Queue<RideRequest> queue;

    public RideQueue(QueuePolicy policy) {
        this.policy = policy;
        this.queue = createQueue(policy);
    }

    private static Queue<RideRequest> createQueue(QueuePolicy policy) {
        return policy == QueuePolicy.FIFO ? new ArrayDeque<>() : new PriorityQueue<>(PRIORITY_ORDER);
    }

    public QueuePolicy getPolicy() {
        return policy;
    }

    /**
     * Switches policy and moves the waiting requests into the new queue.
     * They are re-added in id order so FIFO stays first-come, first-served.
     */
    public void setPolicy(QueuePolicy newPolicy) {
        List<RideRequest> waiting = new ArrayList<>(queue);
        waiting.sort(Comparator.comparingInt(RideRequest::getId));
        policy = newPolicy;
        queue = createQueue(newPolicy);
        queue.addAll(waiting);
    }

    /** Adds a request. A request already in the queue is not added twice. */
    public boolean add(RideRequest request) {
        if (queue.contains(request)) {
            return false;
        }
        return queue.offer(request);
    }

    /** The next request to process, without removing it. */
    public RideRequest peek() {
        return queue.peek();
    }

    /** Removes and returns the next request to process. */
    public RideRequest poll() {
        return queue.poll();
    }

    /** Removes a specific request, for example when it is cancelled. */
    public boolean remove(RideRequest request) {
        return queue.remove(request);
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Requests in the order they would be processed. A PriorityQueue's iterator
     * is not sorted, so we poll a copy of it instead.
     */
    public List<RideRequest> inProcessingOrder() {
        Queue<RideRequest> copy = createQueue(policy);
        copy.addAll(queue);
        List<RideRequest> ordered = new ArrayList<>();
        while (!copy.isEmpty()) {
            ordered.add(copy.poll());
        }
        return ordered;
    }
}
