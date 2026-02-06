package com.legymernok.backend.model.mission;

public enum VerificationStatus {
    PENDING,    // Várakozik a tesztelésre / fut a teszt
    SUCCESS,    // A tesztek sikeresek voltak
    FAILED,     // A tesztek sikertelenek voltak
    REVIEW_NEEDED, // Admin review szükséges
    DRAFT       // Még szerkesztés alatt, nem került tesztelésre
}
