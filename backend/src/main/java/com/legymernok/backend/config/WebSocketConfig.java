package com.legymernok.backend.config;

import com.legymernok.backend.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Ugyanazok az origin-ek, mint a SecurityConfig CORS-listáján — lásd ott a
    // megjegyzést arról, hogy a nyilvános domaint miért kell explicit felvenni.
    private static final String[] ALLOWED_ORIGIN_PATTERNS = {
            "http://localhost:3000",
            "http://127.0.0.1:3000",
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "https://legymernok.ujjweb.hu"
    };

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

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
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS)
                .withSockJS(); // Fallback opció régebbi böngészőkhöz

        registry.addEndpoint("/ws-mission-logs")
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Tényleges auth/authz itt történik STOMP CONNECT/SUBSCRIBE frame-eken —
        // lásd StompAuthChannelInterceptor a teljes indoklásért.
        registration.interceptors(stompAuthChannelInterceptor);
    }
}