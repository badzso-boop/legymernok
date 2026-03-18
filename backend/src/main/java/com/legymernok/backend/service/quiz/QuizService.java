package com.legymernok.backend.service.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.dto.quiz.QuizDefinition;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.exception.UnauthorizedAccessException;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.*;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.mission.MissionResultRepository;
import com.legymernok.backend.repository.quiz.QuizSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizService {
    private final GiteaService giteaService;
    private final QuizSessionRepository quizSessionRepository;
    private final MissionRepository missionRepository;
    private final MissionResultRepository missionResultRepository;
    private final ObjectMapper objectMapper; // JSON deszerializáláshoz

    @Transactional
    public QuizDefinition startQuiz(UUID missionId, Cadet cadet) throws Exception {
        // 1. Megkeressük a küldetést
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));

        if (mission.getMissionType() != MissionType.QUIZ) {
            throw new ResourceConflictException("Mission", "type", mission.getMissionType(),
                    "Ez a küldetés nem kvíz típusú, így nem indítható el a kvíz motorral.");
        }

        // 2. Megnézzük, van-e már aktív munkamenet
        Optional<QuizSession> existingSession = quizSessionRepository.findByMissionIdAndCadetId(missionId, cadet.getId());

        QuizDefinition fullQuiz;
        QuizSession session;

        if (existingSession.isPresent()) {
            session = existingSession.get();
            fullQuiz = objectMapper.readValue(session.getQuizSnapshot(), QuizDefinition.class);
            log.info("Resuming existing quiz session for mission {}", missionId);
        } else {
            // 3. Letöltjük a kvízt a Giteából
            String owner = giteaService.getAdminUsername();
            String repoName = mission.getId().toString(); // A repó neve a misszió UUID-je
            String jsonContent = giteaService.getFileContent(owner, repoName, "quiz.json");

            if (jsonContent == null) {
                throw new ResourceNotFoundException("QuizDefinition", "missionId", missionId);
            }

            fullQuiz = objectMapper.readValue(jsonContent, QuizDefinition.class);

            // 4. Munkamenet létrehozása
            Instant now = Instant.now();
            int timeLimit = fullQuiz.getConfig().getTimeLimitSeconds();

            session = QuizSession.builder()
                    .mission(mission)
                    .cadet(cadet)
                    .startTime(now)
                    .endTimeLimit(now.plus(Duration.ofSeconds(timeLimit)))
                    .quizSnapshot(jsonContent)
                    .build();

            quizSessionRepository.save(session);
            log.info("Started new quiz session for mission {}", missionId);
        }

        // 5. Biztonsági szűrés: megoldások eltávolítása a válaszból
        return stripAnswers(fullQuiz);
    }

    @Transactional
    public void syncProgress(UUID missionId, Cadet cadet, Map<String, Object> answers) throws Exception {
        QuizSession session = quizSessionRepository.findByMissionIdAndCadetId(missionId, cadet.getId())
                .orElseThrow(() -> new ResourceNotFoundException("QuizSession", "missionId", missionId));

        session.setCurrentAnswers(objectMapper.writeValueAsString(answers));
        quizSessionRepository.save(session);
    }

    @Transactional
    public MissionResult submitQuiz(UUID missionId, Cadet cadet, Map<String, List<String>> userAnswers) throws Exception {
        QuizSession session = quizSessionRepository.findByMissionIdAndCadetId(missionId, cadet.getId())
                .orElseThrow(() -> new ResourceNotFoundException("QuizSession", "missionId", missionId));

        String submissionHash = generateSubmissionHash(userAnswers);
        Optional<MissionResult> duplicate = missionResultRepository.findByMissionIdAndCadetIdAndSubmissionHash(
                missionId, cadet.getId(), submissionHash);

        if (duplicate.isPresent()) {
            ResourceConflictException ex = new ResourceConflictException(
                    "MissionResult",
                    "submissionHash",
                    submissionHash,
                    "Ezeket a válaszokat már beküldted korábban. Itt az akkori eredményed:"
            );
            ex.setData(duplicate.orElseThrow());
            throw ex;
        }

        boolean isLate = Instant.now().isAfter(session.getEndTimeLimit());

        QuizDefinition definition = objectMapper.readValue(session.getQuizSnapshot(), QuizDefinition.class);
        double totalScore = 0;
        double maxPossibleScore = 0;

        for (QuizDefinition.QuizQuestionDTO question : definition.getQuestions()) {
            maxPossibleScore += question.getPoints();

            List<String> submittedIds = userAnswers.getOrDefault(question.getId(), List.of());

            List<String> correctIds = question.getOptions().stream()
                    .filter(o -> o.getIsCorrect() != null && o.getIsCorrect())
                    .map(QuizDefinition.QuizOptionDTO::getId)
                    .toList();

            if (correctIds.isEmpty()) continue;

            double pointsPerCorrect = (double) question.getPoints() / correctIds.size();
            double questionScore = 0;

            for (String optionId : submittedIds) {
                if (correctIds.contains(optionId)) {
                    questionScore += pointsPerCorrect;
                } else {
                    questionScore -= pointsPerCorrect;
                }
            }

            totalScore += Math.max(0, questionScore);
        }

        double percentage = (maxPossibleScore > 0) ? (totalScore / maxPossibleScore) * 100 : 0;

        MissionResult result = MissionResult.builder()
                .cadet(cadet)
                .mission(session.getMission())
                .score(totalScore)
                .maxScore(maxPossibleScore)
                .percentage(percentage)
                .detailedAnswers(objectMapper.writeValueAsString(userAnswers))
                .submissionHash(submissionHash)
                .isLate(isLate)
                .build();

        missionResultRepository.save(result);
        quizSessionRepository.delete(session);

        return result;
    }

    @Transactional
    public void clearAllSessions(UUID missionId, Cadet cadet) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));

        boolean isOwner = mission.getOwner().getId().equals(cadet.getId());
        boolean canEditAny = cadet.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("mission:edit_any"));

        if (!isOwner && !canEditAny) {
            throw new UnauthorizedAccessException("Nincs jogosultságod törölni más misszió sessionjeit.");
        }

        quizSessionRepository.deleteAllByMissionId(missionId);
        log.info("Cleared all quiz sessions for mission {} by cadet {}", missionId, cadet.getUsername());
    }

    private String generateSubmissionHash(Map<String, List<String>> answers) {
        try {
            // 1. Sorba rendezzük a kérdéseket és a válaszokat, hogy determinisztikus legyen
            TreeMap<String, List<String>> sortedAnswers = new TreeMap<>(answers);
            sortedAnswers.forEach((k, v) -> Collections.sort(v));

            // 2. Szöveggé alakítjuk
            String content = sortedAnswers.toString();

            // 3. SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));

            // 4. Hexadecimális stringgé alakítjuk
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate submission hash", e);
        }
    }

    private QuizDefinition stripAnswers(QuizDefinition fullQuiz) {
        fullQuiz.getQuestions().forEach(q -> {
            // isMulti kiszámítása isCorrect törlése ELŐTT
            long correctCount = q.getOptions().stream()
                    .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                    .count();
            q.setIsMulti(correctCount > 1);
            // isCorrect törlése — kliens nem láthatja a helyes válaszokat
            q.getOptions().forEach(o -> o.setIsCorrect(null));
        });
        return fullQuiz;
    }
}
