package com.legymernok.backend.web.chat;

import com.legymernok.backend.dto.chat.ChatRequest;
import com.legymernok.backend.dto.chat.ChatResponse;
import com.legymernok.backend.service.ai.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(chatService.chat(request, authentication.getName()));
    }
}
