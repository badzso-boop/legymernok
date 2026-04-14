package com.legymernok.backend.service.mission;

import com.legymernok.backend.dto.group.*;
import com.legymernok.backend.dto.mission.MissionResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.exception.UnauthorizedAccessException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionGroup;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionGroupRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionGroupService {

    private final MissionGroupRepository missionGroupRepository;
    private final MissionRepository missionRepository;
    private final StarSystemRepository starSystemRepository;
    private final CadetRepository cadetRepository;
    private final MissionService missionService;

    @Transactional
    public MissionGroupResponse createGroup(CreateMissionGroupRequest request) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        var starSystem = starSystemRepository.findById(request.getStarSystemId())
                .orElseThrow(() -> new ResourceNotFoundException("StarSystem", "id", request.getStarSystemId()));

        int orderIndex = request.getOrderIndex() != null
                ? request.getOrderIndex()
                : Math.max(
                        missionGroupRepository.findMaxOrderIndex(request.getStarSystemId()),
                        missionRepository.findMaxOrderIndex(request.getStarSystemId())
                  ) + 1;

        // Ha az orderIndex foglalt: mindkét táblában eltolás
        if (missionGroupRepository.existsByStarSystemIdAndOrderIndex(request.getStarSystemId(), orderIndex)) {
            missionGroupRepository.shiftOrdersUp(request.getStarSystemId(), orderIndex);
            missionRepository.shiftOrdersUp(request.getStarSystemId(), orderIndex);
            missionGroupRepository.flush();
            missionRepository.flush();
        }

        MissionGroup group = MissionGroup.builder()
                .starSystem(starSystem)
                .name(request.getName())
                .description(request.getDescription())
                .owner(currentUser)
                .orderIndex(orderIndex)
                .build();

        MissionGroup saved = missionGroupRepository.save(group);
        log.info("MissionGroup '{}' created by '{}'", saved.getName(), currentUser.getUsername());
        return mapToResponse(saved);
    }

    @Transactional
    public MissionGroupResponse updateGroup(UUID id, CreateMissionGroupRequest request) {
        MissionGroup group = missionGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MissionGroup", "id", id));

        group.setName(request.getName());
        group.setDescription(request.getDescription());
        return mapToResponse(missionGroupRepository.save(group));
    }

    @Transactional
    public void deleteGroup(UUID id) {
        MissionGroup group = missionGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MissionGroup", "id", id));

        List<Mission> groupMissions = missionRepository.findAllByGroupIdOrderByGroupOrderAsc(id);

        // FILL_IN_BLANK mission esetén törlés megtagadva
        boolean hasFillInBlank = groupMissions.stream()
                .anyMatch(m -> m.getMissionType() == MissionType.FILL_IN_BLANK);
        if (hasFillInBlank) {
            throw new ResourceConflictException("MissionGroup", "id",
                    "A csoport FILL_IN_BLANK missziót tartalmaz — töröld előbb.");
        }

        int groupOrderIndex = group.getOrderIndex();
        UUID starSystemId = group.getStarSystem().getId();
        int n = groupMissions.size();

        // 1. Előbb: minden meglévő standalone mission és group amelynek orderIndex > groupOrderIndex
        //    kap +N-t, hogy helyet csináljunk az új standalone misszióknak
        if (n > 0) {
            missionGroupRepository.shiftOrdersUp(starSystemId, groupOrderIndex + 1);
            missionRepository.shiftOrdersUp(starSystemId, groupOrderIndex + 1);
            missionGroupRepository.flush();
            missionRepository.flush();
        }

        // 2. Ezután: group missziók standalone-á válnak, orderIndex = groupOrderIndex + i
        for (int i = 0; i < groupMissions.size(); i++) {
            Mission m = groupMissions.get(i);
            m.setGroup(null);
            m.setGroupOrder(null);
            m.setOrderIndex(groupOrderIndex + i);
            missionRepository.save(m);
        }
        missionRepository.flush();

        // 3. Group törlése
        missionGroupRepository.delete(group);
        missionGroupRepository.flush();
        log.info("MissionGroup '{}' deleted, {} missions became standalone", group.getName(), n);
    }

    @Transactional
    public MissionGroupResponse addMissionToGroup(UUID groupId, UUID missionId) {
        MissionGroup group = missionGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("MissionGroup", "id", groupId));
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));

        if (mission.getGroup() != null) {
            ResourceConflictException ex = new ResourceConflictException("Mission", "id",
                    "A misszió már hozzá van rendelve egy másik csoporthoz");
            ex.setData(new java.util.HashMap<String, Object>() {{
                put("conflictingGroupId", mission.getGroup().getId());
                put("conflictingGroupName", mission.getGroup().getName());
            }});
            throw ex;
        }

        int nextGroupOrder = missionRepository.findNextGroupOrder(groupId);
        mission.setGroup(group);
        mission.setGroupOrder(nextGroupOrder);
        mission.setOrderIndex(null);
        missionRepository.save(mission);

        return mapToResponse(group);
    }

    @Transactional
    public MissionGroupResponse removeMissionFromGroup(UUID groupId, UUID missionId) {
        MissionGroup group = missionGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("MissionGroup", "id", groupId));
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));

        if (mission.getMissionType() == MissionType.FILL_IN_BLANK) {
            throw new ResourceConflictException("Mission", "id",
                    "FILL_IN_BLANK misszió nem lehet standalone.");
        }

        int newOrderIndex = missionRepository.findMaxOrderIndex(group.getStarSystem().getId()) + 1;
        mission.setGroup(null);
        mission.setGroupOrder(null);
        mission.setOrderIndex(newOrderIndex);
        missionRepository.save(mission);

        return mapToResponse(group);
    }

    @Transactional
    public ReorderResponse reorderGroup(UUID groupId, UUID targetId) {
        MissionGroup group = missionGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("MissionGroup", "id", groupId));

        // targetId lehet MissionGroup vagy standalone Mission
        Integer targetOrderIndex = null;
        UUID targetUUID = targetId;

        var targetGroup = missionGroupRepository.findById(targetId);
        if (targetGroup.isPresent()) {
            targetOrderIndex = targetGroup.get().getOrderIndex();
            // Swap
            int tmpOrder = group.getOrderIndex();
            group.setOrderIndex(targetOrderIndex);
            targetGroup.get().setOrderIndex(tmpOrder);
            missionGroupRepository.save(group);
            missionGroupRepository.save(targetGroup.get());
            return ReorderResponse.builder()
                    .updated(List.of(
                            ReorderResponse.ReorderItem.builder().id(groupId).orderIndex(group.getOrderIndex()).build(),
                            ReorderResponse.ReorderItem.builder().id(targetId).orderIndex(targetGroup.get().getOrderIndex()).build()
                    ))
                    .build();
        }

        var targetMission = missionRepository.findById(targetId);
        if (targetMission.isPresent() && targetMission.get().getGroup() == null) {
            int tmpOrder = group.getOrderIndex();
            group.setOrderIndex(targetMission.get().getOrderIndex());
            targetMission.get().setOrderIndex(tmpOrder);
            missionGroupRepository.save(group);
            missionRepository.save(targetMission.get());
            return ReorderResponse.builder()
                    .updated(List.of(
                            ReorderResponse.ReorderItem.builder().id(groupId).orderIndex(group.getOrderIndex()).build(),
                            ReorderResponse.ReorderItem.builder().id(targetId).orderIndex(targetMission.get().getOrderIndex()).build()
                    ))
                    .build();
        }

        throw new ResourceNotFoundException("Item", "id", targetId);
    }

    @Transactional
    public ReorderResponse reorderMissionInGroup(UUID groupId, UUID missionId, UUID targetMissionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));
        Mission target = missionRepository.findById(targetMissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", targetMissionId));

        int tmpOrder = mission.getGroupOrder();
        mission.setGroupOrder(target.getGroupOrder());
        target.setGroupOrder(tmpOrder);
        missionRepository.save(mission);
        missionRepository.save(target);

        return ReorderResponse.builder()
                .updated(List.of(
                        ReorderResponse.ReorderItem.builder().id(missionId).orderIndex(mission.getGroupOrder()).build(),
                        ReorderResponse.ReorderItem.builder().id(targetMissionId).orderIndex(target.getGroupOrder()).build()
                ))
                .build();
    }

    @Transactional(readOnly = true)
    public MissionGroupWithMissionsResponse getGroupWithMissions(UUID groupId) {
        MissionGroup group = missionGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("MissionGroup", "id", groupId));

        List<MissionResponse> missions = missionRepository
                .findAllByGroupIdOrderByGroupOrderAsc(groupId).stream()
                .map(missionService::mapToResponse)
                .collect(Collectors.toList());

        return MissionGroupWithMissionsResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .starSystemId(group.getStarSystem().getId())
                .starSystemName(group.getStarSystem().getName())
                .orderIndex(group.getOrderIndex())
                .missions(missions)
                .build();
    }

    public MissionGroupResponse mapToResponse(MissionGroup group) {
        return MissionGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .starSystemId(group.getStarSystem().getId())
                .orderIndex(group.getOrderIndex())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    private Cadet getCurrentAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }
}
