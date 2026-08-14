package com.legymernok.backend.service.social;

import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.social.Follow;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.social.FollowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock private FollowRepository followRepository;
    @Mock private CadetRepository cadetRepository;
    @Mock private Authentication mockAuthentication;

    private FollowService followService;

    private Cadet currentUser;

    @BeforeEach
    void setUp() {
        followService = new FollowService(followRepository, cadetRepository);
        currentUser = Cadet.builder().id(UUID.randomUUID()).username("kadét").build();

        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(mockAuthentication);
        SecurityContextHolder.setContext(securityContext);
        lenient().when(mockAuthentication.getName()).thenReturn(currentUser.getUsername());
        lenient().when(cadetRepository.findByUsername(currentUser.getUsername())).thenReturn(Optional.of(currentUser));
    }

    @Test
    void follow_CannotFollowSelf() {
        assertThrows(ResourceConflictException.class, () -> followService.follow(currentUser.getId()));
        verify(followRepository, never()).save(any());
    }

    @Test
    void follow_UnknownFollowee_ThrowsNotFound() {
        UUID followeeId = UUID.randomUUID();
        when(cadetRepository.findById(followeeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> followService.follow(followeeId));
    }

    @Test
    void follow_AlreadyFollowing_IsIdempotent() {
        Cadet followee = Cadet.builder().id(UUID.randomUUID()).username("masik").build();
        when(cadetRepository.findById(followee.getId())).thenReturn(Optional.of(followee));
        when(followRepository.existsByFollower_IdAndFollowee_Id(currentUser.getId(), followee.getId()))
                .thenReturn(true);

        followService.follow(followee.getId());

        verify(followRepository, never()).save(any());
    }

    @Test
    void follow_NewFollowee_Saves() {
        Cadet followee = Cadet.builder().id(UUID.randomUUID()).username("masik").build();
        when(cadetRepository.findById(followee.getId())).thenReturn(Optional.of(followee));
        when(followRepository.existsByFollower_IdAndFollowee_Id(currentUser.getId(), followee.getId()))
                .thenReturn(false);

        followService.follow(followee.getId());

        verify(followRepository).save(any(Follow.class));
    }

    @Test
    void unfollow_DelegatesToRepository() {
        UUID followeeId = UUID.randomUUID();
        followService.unfollow(followeeId);
        verify(followRepository).deleteByFollower_IdAndFollowee_Id(currentUser.getId(), followeeId);
    }

    @Test
    void getFollowing_MapsToSummaries() {
        Cadet followee = Cadet.builder().id(UUID.randomUUID()).username("kovetett").build();
        Follow follow = Follow.builder().follower(currentUser).followee(followee).build();
        when(followRepository.findAllByFollower_Id(currentUser.getId())).thenReturn(List.of(follow));

        List<?> result = followService.getFollowing(currentUser.getId());

        assertEquals(1, result.size());
    }
}
