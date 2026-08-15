package com.legymernok.backend.service.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebSocketLogService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendLog(String logMessage) {
        // Elküldjük a logot minden feliratkozónak a /topic/logs csatornára
        try {
            messagingTemplate.convertAndSend("/topic/logs", logMessage);
        } catch (Exception e) {
            // Ha a WebSocket még nem állt fel (pl. induláskor), lenyeljük a hibát
            // Különben végtelen ciklus lenne (hiba -> log -> hiba...)
        }
    }
}
