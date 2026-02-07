package com.legymernok.backend.repository.cadet;

import com.legymernok.backend.model.cadet.Cadet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CadetRepository extends JpaRepository<Cadet, UUID> {
    Optional<Cadet> findByUsername(String username);
    Optional<Cadet> findByEmail(String email);
    List<Cadet> findAllByRoles_Id(UUID roleId);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<Cadet> findFirstByRoles_Permissions_Name(String permissionName);
}
