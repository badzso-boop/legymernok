package com.legymernok.backend.security;

import com.legymernok.backend.model.auth.Permission;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.service.mission.MissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    @Mock
    private MissionService missionService;

    private StompAuthChannelInterceptor interceptor;
    private Cadet cadet;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtService, userDetailsService, missionService);
        cadet = new Cadet();
        cadet.setId(UUID.randomUUID());
        cadet.setUsername("test_cadet");
        cadet.setRoles(new HashSet<>());
    }

    private void grantAuthority(String authorityName) {
        Permission permission = new Permission();
        permission.setName(authorityName);
        Role role = new Role();
        role.setName("MOCK_ROLE");
        role.setPermissions(Set.of(permission));
        cadet.setRoles(Set.of(role));
    }

    private Principal authenticatedCadet() {
        return new UsernamePasswordAuthenticationToken(cadet, null, cadet.getAuthorities());
    }

    private Message<byte[]> connectMessage(String authHeaderValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeaderValue != null) {
            accessor.addNativeHeader("Authorization", authHeaderValue);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage(String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (user != null) {
            accessor.setUser(user);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // =========================================================================
    // CONNECT (authenticate)
    // =========================================================================

    @Test
    void connect_withoutAuthorizationHeader_shouldReject() {
        Message<byte[]> message = connectMessage(null);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void connect_withNonBearerAuthorizationHeader_shouldReject() {
        Message<byte[]> message = connectMessage("Basic dXNlcjpwYXNz");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void connect_withUnparseableToken_shouldReject() {
        when(jwtService.extractUsername("bad-token")).thenThrow(new RuntimeException("malformed JWT"));
        Message<byte[]> message = connectMessage("Bearer bad-token");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void connect_whenUserNoLongerExists_shouldReject() {
        when(jwtService.extractUsername("token")).thenReturn("ghost");
        when(userDetailsService.loadUserByUsername("ghost")).thenThrow(new UsernameNotFoundException("no such user"));
        Message<byte[]> message = connectMessage("Bearer token");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void connect_whenTokenFailsValidation_shouldReject() {
        when(jwtService.extractUsername("token")).thenReturn(cadet.getUsername());
        when(userDetailsService.loadUserByUsername(cadet.getUsername())).thenReturn(cadet);
        when(jwtService.isTokenValid("token", cadet)).thenReturn(false);
        Message<byte[]> message = connectMessage("Bearer token");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void connect_withValidToken_shouldAuthenticateAndSetUserOnAccessor() {
        when(jwtService.extractUsername("token")).thenReturn(cadet.getUsername());
        when(userDetailsService.loadUserByUsername(cadet.getUsername())).thenReturn(cadet);
        when(jwtService.isTokenValid("token", cadet)).thenReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertDoesNotThrow(() -> interceptor.preSend(message, null));

        assertNotNull(accessor.getUser());
        assertInstanceOf(UsernamePasswordAuthenticationToken.class, accessor.getUser());
        assertEquals(cadet, ((UsernamePasswordAuthenticationToken) accessor.getUser()).getPrincipal());
    }

    // =========================================================================
    // SUBSCRIBE (authorizeSubscription)
    // =========================================================================

    @Test
    void subscribe_withoutAuthenticatedUser_shouldReject() {
        Message<byte[]> message = subscribeMessage("/topic/logs", null);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void subscribe_withNonCadetPrincipal_shouldReject() {
        Principal notACadet = new UsernamePasswordAuthenticationToken("plain-string-principal", null);
        Message<byte[]> message = subscribeMessage("/topic/logs", notACadet);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void subscribe_toLogsTopic_withoutLogsReadAuthority_shouldReject() {
        Message<byte[]> message = subscribeMessage("/topic/logs", authenticatedCadet());
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void subscribe_toLogsTopic_withLogsReadAuthority_shouldAllow() {
        grantAuthority("logs:read");
        Message<byte[]> message = subscribeMessage("/topic/logs", authenticatedCadet());
        assertDoesNotThrow(() -> interceptor.preSend(message, null));
    }

    @Test
    void subscribe_toMissionTopic_whenServiceAllows_shouldAllow() {
        UUID missionId = UUID.randomUUID();
        when(missionService.canViewMissionLogs(eq(missionId), eq(cadet))).thenReturn(true);
        Message<byte[]> message = subscribeMessage("/topic/mission/" + missionId, authenticatedCadet());
        assertDoesNotThrow(() -> interceptor.preSend(message, null));
    }

    @Test
    void subscribe_toMissionTopic_whenServiceDenies_shouldReject() {
        UUID missionId = UUID.randomUUID();
        when(missionService.canViewMissionLogs(eq(missionId), eq(cadet))).thenReturn(false);
        Message<byte[]> message = subscribeMessage("/topic/mission/" + missionId, authenticatedCadet());
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void subscribe_toMissionTopicWithNonUuidId_shouldRejectWithoutCallingMissionService() {
        Message<byte[]> message = subscribeMessage("/topic/mission/not-a-uuid", authenticatedCadet());
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
        verifyNoInteractions(missionService);
    }

    // Régebbi, laza regex ([0-9a-fA-F-]{36}) mellett ez a destination illeszkedett
    // volna, de UUID.fromString() IllegalArgumentException-t dobott volna rá,
    // kezeletlenül. A szigorított regex mellett ez már nem is jut el a
    // UUID.fromString() hívásig — de bármelyik réteg is fogja meg, a végeredmény
    // mindenképp tiszta AccessDeniedException kell legyen, nem valami más.
    @Test
    void subscribe_toMissionTopicWithWrongDashPlacement_shouldRejectCleanly() {
        String looksLikeThirtySixChars = "-".repeat(36);
        Message<byte[]> message = subscribeMessage("/topic/mission/" + looksLikeThirtySixChars, authenticatedCadet());
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
        verifyNoInteractions(missionService);
    }

    @Test
    void subscribe_toUnknownTopic_shouldReject() {
        Message<byte[]> message = subscribeMessage("/topic/something-else", authenticatedCadet());
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    // =========================================================================
    // Egyéb STOMP parancsok (SEND, DISCONNECT stb.) — nem érintettek
    // =========================================================================

    @Test
    void otherCommands_shouldPassThroughWithoutAnyChecks() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertDoesNotThrow(() -> interceptor.preSend(message, null));
        verifyNoInteractions(jwtService, missionService);
    }
}
