package com.legymernok.backend.security;

import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.service.mission.MissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A /ws-log és /ws-mission-logs STOMP endpointok HTTP-handshake szinten szándékosan
// permitAll()-ok (SecurityConfig) — SockJS-en keresztül a kliens nem tud egyedi
// Authorization headert küldeni a handshake-hez. A tényleges hitelesítés/jogosultság-
// ellenőrzés itt, a STOMP CONNECT és SUBSCRIBE frame-eken történik: a kliens a JWT-t
// STOMP connectHeaders-ként küldi ("Authorization: Bearer <token>"), ezt validáljuk
// CONNECT-kor, majd minden SUBSCRIBE-nál külön ellenőrizzük a célt (topic-ot).
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern MISSION_TOPIC = Pattern.compile("^/topic/mission/([0-9a-fA-F-]{36})$");

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final MissionService missionService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("STOMP CONNECT rejected: missing Authorization header");
            throw new AccessDeniedException("Missing or invalid Authorization header");
        }

        String jwt = authHeader.substring(7);
        String username;
        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            log.warn("STOMP CONNECT rejected: invalid token");
            throw new AccessDeniedException("Invalid token");
        }

        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(username);
        } catch (Exception e) {
            log.warn("STOMP CONNECT rejected: user not found");
            throw new AccessDeniedException("Invalid token");
        }

        if (!jwtService.isTokenValid(jwt, userDetails)) {
            log.warn("STOMP CONNECT rejected: token failed validation");
            throw new AccessDeniedException("Invalid token");
        }

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        accessor.setUser(authToken);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        UsernamePasswordAuthenticationToken principal =
                (UsernamePasswordAuthenticationToken) accessor.getUser();
        if (principal == null || !(principal.getPrincipal() instanceof Cadet cadet)) {
            log.warn("STOMP SUBSCRIBE rejected: unauthenticated ({})", destination);
            throw new AccessDeniedException("Unauthenticated");
        }

        if ("/topic/logs".equals(destination)) {
            boolean allowed = cadet.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(authority -> authority.equals("logs:read"));
            if (!allowed) {
                log.warn("STOMP SUBSCRIBE rejected: '{}' has no logs:read", cadet.getUsername());
                throw new AccessDeniedException("Missing logs:read authority");
            }
            return;
        }

        Matcher matcher = MISSION_TOPIC.matcher(destination);
        if (matcher.matches()) {
            UUID missionId = UUID.fromString(matcher.group(1));
            if (!missionService.canViewMissionLogs(missionId, cadet)) {
                log.warn("STOMP SUBSCRIBE rejected: '{}' cannot view logs for mission {}", cadet.getUsername(), missionId);
                throw new AccessDeniedException("Not allowed to view this mission's logs");
            }
            return;
        }

        // Ismeretlen topic mintázat — alapértelmezésben tiltjuk, ne engedjünk
        // véletlenül hozzáférést egy jövőbeli, még nem ismert csatornához.
        log.warn("STOMP SUBSCRIBE rejected: unknown destination '{}'", destination);
        throw new AccessDeniedException("Unknown destination");
    }
}
