package com.legymernok.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Erre a prefixre iratkozhat fel a kliens (pl. /topic/logs)
        config.enableSimpleBroker("/topic");
        // Erre a prefixre küldhet üzenetet a kliens (ha kéne, de most csak hallgat)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Ez a végpont, ahova a kliens csatlakozik (handshake)
        registry.addEndpoint("/ws-log")
                .setAllowedOriginPatterns("*") // Fejlesztéshez engedjük mindenhonnan
                .withSockJS(); // Fallback opció régebbi böngészőkhöz
    }
}