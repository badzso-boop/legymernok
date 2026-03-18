package com.legymernok.backend.service.mission;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MissionLogService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendMissionLog(UUID missionId, String message) {
        messagingTemplate.convertAndSend("/topic/mission/" + missionId, message);
    }
}
