package com.legymernok.backend.repository.cadet;

import com.legymernok.backend.model.cadet.Cadet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CadetRepository extends JpaRepository<Cadet, UUID> {
    Optional<Cadet> findByUsername(String username);
    Optional<Cadet> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
