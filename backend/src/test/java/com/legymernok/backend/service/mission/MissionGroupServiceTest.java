package com.legymernok.backend.service.mission;

import com.legymernok.backend.dto.group.CreateMissionGroupRequest;
import com.legymernok.backend.dto.group.MissionGroupResponse;
import com.legymernok.backend.dto.group.ReorderResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionGroup;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionGroupRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
class MissionGroupServiceTest {

    @Mock private MissionGroupRepository missionGroupRepository;
    @Mock private MissionRepository missionRepository;
    @Mock private StarSystemRepository starSystemRepository;
    @Mock private CadetRepository cadetRepository;
    @Mock private MissionService missionService;
    @InjectMocks private MissionGroupService missionGroupService;

    private Cadet testUser;
    private StarSystem testStarSystem;

    @BeforeEach
    void setUp() {
        testUser = new Cadet();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("test_user");

        testStarSystem = new StarSystem();
        testStarSystem.setId(UUID.randomUUID());
        testStarSystem.setName("Test System");
        testStarSystem.setOwner(testUser);

        Authentication auth = mock(Authentication.class);
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
        lenient().when(auth.getName()).thenReturn("test_user");
        lenient().when(cadetRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
    }

    // =========================================================================
    // createGroup tesztek
    // =========================================================================

    @Test
    void createGroup_whenOrderIndexIsNull_shouldAppendToEnd() {
        CreateMissionGroupRequest request = new CreateMissionGroupRequest();
        request.setStarSystemId(testStarSystem.getId());
        request.setName("New Group");
        request.setOrderIndex(null);

        when(starSystemRepository.findById(testStarSystem.getId())).thenReturn(Optional.of(testStarSystem));
        when(missionGroupRepository.findMaxOrderIndex(testStarSystem.getId())).thenReturn(2);
        when(missionRepository.findMaxOrderIndex(testStarSystem.getId())).thenReturn(3);
        when(missionGroupRepository.existsByStarSystemIdAndOrderIndex(testStarSystem.getId(), 4)).thenReturn(false);
        when(missionGroupRepository.save(any(MissionGroup.class))).thenAnswer(i -> {
            MissionGroup g = i.getArgument(0);
            g.setId(UUID.randomUUID());
            g.setStarSystem(testStarSystem);
            return g;
        });

        MissionGroupResponse response = missionGroupService.createGroup(request);

        assertNotNull(response);
        assertEquals(4, response.getOrderIndex());
        verify(missionGroupRepository, never()).shiftOrdersUp(any(), anyInt());
        verify(missionRepository, never()).shiftOrdersUp(any(), anyInt());
    }

    @Test
    void createGroup_whenOrderIndexConflicts_shouldShiftBothTables() {
        CreateMissionGroupRequest request = new CreateMissionGroupRequest();
        request.setStarSystemId(testStarSystem.getId());
        request.setName("Conflicting Group");
        request.setOrderIndex(2);

        when(starSystemRepository.findById(testStarSystem.getId())).thenReturn(Optional.of(testStarSystem));
        when(missionGroupRepository.existsByStarSystemIdAndOrderIndex(testStarSystem.getId(), 2)).thenReturn(true);
        when(missionGroupRepository.save(any(MissionGroup.class))).thenAnswer(i -> {
            MissionGroup g = i.getArgument(0);
            g.setId(UUID.randomUUID());
            g.setStarSystem(testStarSystem);
            return g;
        });

        missionGroupService.createGroup(request);

        verify(missionGroupRepository).shiftOrdersUp(testStarSystem.getId(), 2);
        verify(missionRepository).shiftOrdersUp(testStarSystem.getId(), 2);
    }

    // =========================================================================
    // deleteGroup tesztek
    // =========================================================================

    @Test
    void deleteGroup_whenGroupContainsFillInBlankMission_shouldCascadeDeleteFibAndDeleteGroup() {
        UUID groupId = UUID.randomUUID();
        UUID fibMissionId = UUID.randomUUID();
        MissionGroup group = MissionGroup.builder()
                .id(groupId)
                .starSystem(testStarSystem)
                .name("FIB Group")
                .orderIndex(1)
                .build();

        Mission fillInBlankMission = new Mission();
        fillInBlankMission.setId(fibMissionId);
        fillInBlankMission.setMissionType(MissionType.FILL_IN_BLANK);
        fillInBlankMission.setGroup(group);

        when(missionGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(missionRepository.findAllByGroupIdOrderByGroupOrderAsc(groupId))
                .thenReturn(List.of(fillInBlankMission));

        missionGroupService.deleteGroup(groupId);

        verify(missionService).deleteMission(fibMissionId);
        verify(missionGroupRepository).delete(group);
    }

    @Test
    void deleteGroup_shouldShiftFirstThenConvertMissionsToStandalone() {
        UUID groupId = UUID.randomUUID();
        MissionGroup group = MissionGroup.builder()
                .id(groupId)
                .starSystem(testStarSystem)
                .name("Group With Missions")
                .orderIndex(2)
                .build();

        Mission m1 = new Mission();
        m1.setId(UUID.randomUUID());
        m1.setMissionType(MissionType.CODING);
        m1.setGroupOrder(0);

        Mission m2 = new Mission();
        m2.setId(UUID.randomUUID());
        m2.setMissionType(MissionType.CONTENT);
        m2.setGroupOrder(1);

        Mission m3 = new Mission();
        m3.setId(UUID.randomUUID());
        m3.setMissionType(MissionType.QUIZ);
        m3.setGroupOrder(2);

        when(missionGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(missionRepository.findAllByGroupIdOrderByGroupOrderAsc(groupId))
                .thenReturn(List.of(m1, m2, m3));

        missionGroupService.deleteGroup(groupId);

        // Shift kell (3 misszió → +3 helyet csinál)
        verify(missionGroupRepository).shiftOrdersUp(testStarSystem.getId(), 3); // groupOrderIndex+1 = 3
        verify(missionRepository).shiftOrdersUp(testStarSystem.getId(), 3);

        // Missziók standalone-á válnak és megfelelő orderIndex-et kapnak
        assertNull(m1.getGroup());
        assertEquals(2, m1.getOrderIndex()); // groupOrderIndex + 0
        assertNull(m2.getGroup());
        assertEquals(3, m2.getOrderIndex()); // groupOrderIndex + 1
        assertNull(m3.getGroup());
        assertEquals(4, m3.getOrderIndex()); // groupOrderIndex + 2

        verify(missionGroupRepository).delete(group);
    }

    // =========================================================================
    // addMissionToGroup tesztek
    // =========================================================================

    @Test
    void addMissionToGroup_whenMissionAlreadyInAnotherGroup_shouldThrow409WithConflictData() {
        UUID groupId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        UUID conflictingGroupId = UUID.randomUUID();

        MissionGroup targetGroup = MissionGroup.builder()
                .id(groupId).starSystem(testStarSystem).name("Target").orderIndex(1).build();
        MissionGroup existingGroup = MissionGroup.builder()
                .id(conflictingGroupId).starSystem(testStarSystem).name("Existing Group").orderIndex(2).build();

        Mission mission = new Mission();
        mission.setId(missionId);
        mission.setMissionType(MissionType.CODING);
        mission.setGroup(existingGroup);

        when(missionGroupRepository.findById(groupId)).thenReturn(Optional.of(targetGroup));
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> missionGroupService.addMissionToGroup(groupId, missionId));
        assertNotNull(ex.getData());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) ex.getData();
        assertTrue(data.containsKey("conflictingGroupId"));
        assertEquals(conflictingGroupId, data.get("conflictingGroupId"));
    }

    // =========================================================================
    // removeMissionFromGroup tesztek
    // =========================================================================

    @Test
    void removeMissionFromGroup_whenMissionIsFillInBlank_shouldBecomeStandalone() {
        UUID groupId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();

        MissionGroup group = MissionGroup.builder()
                .id(groupId).starSystem(testStarSystem).name("Group").orderIndex(1).build();

        Mission mission = new Mission();
        mission.setId(missionId);
        mission.setMissionType(MissionType.FILL_IN_BLANK);
        mission.setGroup(group);

        when(missionGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(missionRepository.findMaxOrderIndex(testStarSystem.getId())).thenReturn(2);

        missionGroupService.removeMissionFromGroup(groupId, missionId);

        verify(missionRepository).save(mission);
        assertNull(mission.getGroup());
        assertEquals(3, mission.getOrderIndex());
    }

    // =========================================================================
    // reorderGroup tesztek
    // =========================================================================

    @Test
    void reorderGroup_withAnotherGroup_shouldSwapOrderIndexes() {
        UUID groupAId = UUID.randomUUID();
        UUID groupBId = UUID.randomUUID();

        MissionGroup groupA = MissionGroup.builder()
                .id(groupAId).starSystem(testStarSystem).name("A").orderIndex(3).build();
        MissionGroup groupB = MissionGroup.builder()
                .id(groupBId).starSystem(testStarSystem).name("B").orderIndex(5).build();

        when(missionGroupRepository.findById(groupAId)).thenReturn(Optional.of(groupA));
        when(missionGroupRepository.findById(groupBId)).thenReturn(Optional.of(groupB));

        ReorderResponse response = missionGroupService.reorderGroup(groupAId, groupBId);

        assertEquals(5, groupA.getOrderIndex());
        assertEquals(3, groupB.getOrderIndex());
        assertNotNull(response.getUpdated());
        assertEquals(2, response.getUpdated().size());
    }
}
