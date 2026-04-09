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
        // The client can subscribe to this prefix (e.g. /topic/logs)
        config.enableSimpleBroker("/topic");
        // The client can send messages to this prefix (if needed, but currently only listens)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // This is the endpoint the client connects to (handshake)
        registry.addEndpoint("/ws-log")
                .setAllowedOriginPatterns("*") // Allow from everywhere for development
                .withSockJS(); // Fallback option for older browsers

        registry.addEndpoint("/ws-mission-logs")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}