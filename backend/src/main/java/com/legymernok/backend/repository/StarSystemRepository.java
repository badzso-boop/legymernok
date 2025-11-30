package com.legymernok.backend.repository;

import com.legymernok.backend.model.StarSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StarSystemRepository extends JpaRepository<StarSystem, UUID> {
    Optional<StarSystem> findByName(String name);
    // Itt definiálhatsz majd speciális lekérdezéseket, ha szükséged lesz rájuk
}