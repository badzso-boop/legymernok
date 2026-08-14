package com.legymernok.backend.service.streak;

import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.repository.cadet.CadetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Napi streak (sorozat) nyilvántartása. Lustán számol: nincs éjféli reset job,
 * a megszakadás a következő aktivitáskor derül ki (plans/frontend_redesign_2026.md 7.1).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreakService {

    private final CadetRepository cadetRepository;

    @Transactional
    public void recordActivity(UUID cadetId) {
        Cadet cadet = cadetRepository.findById(cadetId)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "id", cadetId));

        LocalDate today = LocalDate.now();
        LocalDate lastActivity = cadet.getLastActivityDate();

        if (today.equals(lastActivity)) {
            return; // Ma már volt aktivitás, nincs teendő.
        }

        if (lastActivity != null && lastActivity.equals(today.minusDays(1))) {
            cadet.setCurrentStreak(cadet.getCurrentStreak() + 1);
        } else {
            cadet.setCurrentStreak(1);
        }

        cadet.setLongestStreak(Math.max(cadet.getLongestStreak(), cadet.getCurrentStreak()));
        cadet.setLastActivityDate(today);
        cadetRepository.save(cadet);

        log.debug("Streak recorded for cadet {}: currentStreak={}", cadetId, cadet.getCurrentStreak());
    }
}
