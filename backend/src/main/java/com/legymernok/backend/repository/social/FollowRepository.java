package com.legymernok.backend.repository.social;

import com.legymernok.backend.model.social.Follow;
import com.legymernok.backend.model.social.FollowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    boolean existsByFollower_IdAndFollowee_Id(UUID followerId, UUID followeeId);

    void deleteByFollower_IdAndFollowee_Id(UUID followerId, UUID followeeId);

    List<Follow> findAllByFollower_Id(UUID followerId);

    List<Follow> findAllByFollowee_Id(UUID followeeId);

    long countByFollower_Id(UUID followerId);

    long countByFollowee_Id(UUID followeeId);
}
