package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.CompileCircuitResponse;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.integration.GiteaActionsService;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.circuit.BoardType;
import com.legymernok.backend.model.circuit.CadetCircuitSave;
import com.legymernok.backend.model.circuit.CircuitDefinitionStatus;
import com.legymernok.backend.model.circuit.SimulationStatus;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.circuit.CadetCircuitSaveRepository;
import com.legymernok.backend.repository.circuit.CircuitDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Compiles an Arduino sketch by dispatching a Gitea Actions workflow on the cadet's circuit repo.
 *
 * Flow:
 *  1. Resolve cadet / CircuitDefinition / CadetCircuitSave
 *  2. Fetch sketch.ino content → compute content-based cache key (SHA-256 of content + fqbn)
 *  3. Cache check: own save → global (any cadet who compiled the same sketch) → miss → dispatch
 *  4. Dispatch compile.yml via Gitea Actions API
 *  5. Poll until the workflow run completes
 *  6. Download the .hex artifact, encode to Base64
 *  7. Update CadetCircuitSave.simulationStatus + cache fields
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArduinoCompilerService {

    private static final String SKETCH_FILENAME = "sketch.ino";

    private final CadetCircuitSaveRepository saveRepository;
    private final CircuitDefinitionRepository circuitDefinitionRepository;
    private final CadetRepository cadetRepository;
    private final GiteaService giteaService;
    private final GiteaActionsService giteaActionsService;

    @Value("${arduino.compile.poll.timeout-seconds:300}")
    private int pollTimeoutSeconds;

    // --- Public API ---

    /**
     * Compiles the sketch for the given cadet + mission asynchronously.
     * The caller receives a CompletableFuture; the cadet can poll
     * GET /circuit/missions/{missionId} to check simulationStatus progress.
     */
    @Async
    public CompletableFuture<CompileCircuitResponse> compile(String username, UUID missionId) {
        var cadet = cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", username));

        var definition = circuitDefinitionRepository
                .findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CircuitDefinition", "missionId/status", missionId + "/PUBLISHED"));

        CadetCircuitSave save = saveRepository
                .findByCadetIdAndCircuitDefinitionId(cadet.getId(), definition.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CadetCircuitSave", "username/missionId", username + "/" + missionId));

        if (save.getGiteaRepoUrl() == null) {
            return CompletableFuture.completedFuture(
                    failSave(save, "Circuit mission not properly started: missing Gitea repo URL."));
        }

        String repoName = extractRepoName(save.getGiteaRepoUrl());
        String fqbn = toFqbn(definition.getBoardType());

        // --- Content-based compile cache ---
        // Fetch the sketch source to compute a content hash (same code = same hex, regardless of who wrote it)
        String sketchContent = giteaService.getFileContent(giteaService.getAdminUsername(), repoName, SKETCH_FILENAME);
        String cacheKey = buildCacheKey(sketchContent, fqbn);

        // 1. Check own save first (fast path)
        if (cacheKey != null
                && cacheKey.equals(save.getCompileCacheKey())
                && save.getCompiledHexBase64() != null) {
            log.info("Compile cache hit (own save) for save {} (key={})", save.getId(), cacheKey);
            return CompletableFuture.completedFuture(
                    CompileCircuitResponse.cachedSuccess(
                            save.getCompiledHexBase64(), fqbn, definition.getBoardType()));
        }

        // 2. Check global cache: if any other cadet already compiled the exact same sketch, reuse their hex
        if (cacheKey != null) {
            var globalHit = saveRepository
                    .findFirstByCompileCacheKeyAndCompiledHexBase64IsNotNull(cacheKey);
            if (globalHit.isPresent()) {
                String cachedHex = globalHit.get().getCompiledHexBase64();
                log.info("Compile cache hit (global) for save {} (key={})", save.getId(), cacheKey);
                save.setSimulationStatus(SimulationStatus.NEVER_RUN);
                save.setCompiledHexBase64(cachedHex);
                save.setCompileCacheKey(cacheKey);
                saveRepository.save(save);
                return CompletableFuture.completedFuture(
                        CompileCircuitResponse.cachedSuccess(cachedHex, fqbn, definition.getBoardType()));
            }
        }

        // Mark as compiling
        save.setSimulationStatus(SimulationStatus.COMPILING);
        save.setLastCompileError(null);
        saveRepository.save(save);

        long startMs = System.currentTimeMillis();
        try {
            // Dispatch the compile.yml workflow on the cadet's circuit repo
            Instant dispatchedAt = giteaActionsService.dispatchCompileWorkflow(
                    repoName, fqbn, save.getId());

            // Poll until the workflow run completes
            GiteaActionsService.WorkflowRunResult runResult =
                    giteaActionsService.pollUntilComplete(repoName, dispatchedAt, pollTimeoutSeconds);

            if (!runResult.success()) {
                return CompletableFuture.completedFuture(failSave(save,
                        "Compilation workflow failed with conclusion: " + runResult.conclusion()));
            }

            // Download the compiled .hex artifact
            byte[] hexBytes = giteaActionsService.downloadHexArtifact(
                    repoName, runResult.runId(), save.getId());

            long elapsedMs = System.currentTimeMillis() - startMs;
            String hexBase64 = Base64.getEncoder().encodeToString(hexBytes);

            save.setSimulationStatus(SimulationStatus.NEVER_RUN);
            save.setCompilationTimeMs(elapsedMs);
            save.setSimulationStartedAt(null);
            save.setCompiledHexBase64(hexBase64);
            save.setCompileCacheKey(cacheKey);
            saveRepository.save(save);

            log.info("Compile OK for save {} via Gitea Actions in {}ms, hex size {} bytes.",
                    save.getId(), elapsedMs, hexBytes.length);
            return CompletableFuture.completedFuture(
                    CompileCircuitResponse.success(hexBase64, fqbn, definition.getBoardType(), elapsedMs));

        } catch (IOException e) {
            log.error("Artifact download error for save {}: {}", save.getId(), e.getMessage());
            return CompletableFuture.completedFuture(
                    failSave(save, "Failed to retrieve compiled artifact: " + e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Compile workflow error for save {}: {}", save.getId(), e.getMessage());
            return CompletableFuture.completedFuture(failSave(save, e.getMessage()));
        }
    }

    // --- Helpers ---

    private String toFqbn(BoardType boardType) {
        return switch (boardType) {
            case ARDUINO_UNO -> "arduino:avr:uno";
            case ARDUINO_MEGA_2560 -> "arduino:avr:mega:cpu=atmega2560";
            case ESP8266 -> "esp8266:esp8266:nodemcuv2";
            case ESP32 -> "esp32:esp32:esp32";
            default -> throw new IllegalArgumentException(
                    "Board type not supported for compilation: " + boardType);
        };
    }

    /**
     * Builds a content-based cache key from the sketch source and FQBN.
     * The same sketch compiled for the same board always produces the same .hex,
     * so this key is safe to share globally across all cadets.
     *
     * Returns null if sketchContent is null (e.g. empty/new repo) — cache is then bypassed.
     */
    private String buildCacheKey(String sketchContent, String fqbn) {
        if (sketchContent == null || fqbn == null) return null;
        try {
            String input = sketchContent + "|" + fqbn;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available, compile cache disabled");
            return null;
        }
    }

    private String extractRepoName(String cloneUrl) {
        if (cloneUrl == null) return "";
        String path = cloneUrl;
        if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private CompileCircuitResponse failSave(CadetCircuitSave save, String error) {
        save.setSimulationStatus(SimulationStatus.COMPILE_ERROR);
        save.setLastCompileError(error);
        saveRepository.save(save);
        return CompileCircuitResponse.error(error);
    }
}
