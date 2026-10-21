package com.jusmartride.notification;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyRideStatusChanged(String requestId, Object payload) {
        messagingTemplate.convertAndSend("/topic/rides/" + requestId, payload);
    }

    public void notifyDriverNewRequest(String driverId, Object payload) {
        messagingTemplate.convertAndSend("/topic/driver/" + driverId + "/requests", payload);
    }

    public void notifyBroadcastRequest(Object payload) {
        messagingTemplate.convertAndSend("/topic/drivers/requests", payload);
    }

    public void notifyDriverLocation(String requestId, String driverId, Object locationPayload) {
        if (requestId != null) {
            messagingTemplate.convertAndSend("/topic/rides/" + requestId, locationPayload);
        }
        if (driverId != null) {
            messagingTemplate.convertAndSend("/topic/driver/" + driverId + "/location", locationPayload);
        }
    }
}
