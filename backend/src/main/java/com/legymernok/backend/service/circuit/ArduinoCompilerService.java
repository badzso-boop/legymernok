package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.CompileCircuitResponse;
import com.legymernok.backend.exception.ResourceNotFoundException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.scheduling.annotation.Async;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Compiles an Arduino sketch stored in the cadet's Gitea repo.
 *
 * Flow:
 *  1. Fetch sketch.ino from Gitea
 *  2. Write to a temp directory (arduino-cli requires dir name == sketch name)
 *  3. Delegate process execution to ArduinoCliRunner
 *  4. Read the resulting .hex, Base64-encode it
 *  5. Update CadetCircuitSave.simulationStatus + timing fields
 *  6. Return CompileCircuitResponse (hex or error)
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
    private final ArduinoCliRunner cliRunner;

    // --- Public API ---

    /**
     * Compiles the sketch for the given cadet + mission asynchronously.
     * The caller receives a CompletableFuture and can poll the save's simulationStatus
     * (via GET /circuit/missions/{missionId}) to track progress.
     * The returned future resolves to the CompileCircuitResponse once compilation finishes.
     */
    @Async("compilerExecutor")
    @Transactional
    public CompletableFuture<CompileCircuitResponse> compile(String username, UUID missionId) {
        var cadet = cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", username));

        var definition = circuitDefinitionRepository
                .findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("CircuitDefinition", "missionId/status", missionId + "/PUBLISHED"));

        CadetCircuitSave save = saveRepository
                .findByCadetIdAndCircuitDefinitionId(cadet.getId(), definition.getId())
                .orElseThrow(() -> new ResourceNotFoundException("CadetCircuitSave", "username/missionId", username + "/" + missionId));

        if (save.getGiteaRepoUrl() == null) {
            return CompletableFuture.completedFuture(
                    failSave(save, "Circuit mission not properly started: missing Gitea repo URL."));
        }

        String repoName = extractRepoName(save.getGiteaRepoUrl());
        String fqbn = toFqbn(definition.getBoardType());

        // --- Compile cache check ---
        String latestCommit = giteaService.getLatestCommitHash(repoName);
        String cacheKey = buildCacheKey(save.getGiteaRepoUrl(), latestCommit, fqbn);
        if (cacheKey != null
                && cacheKey.equals(save.getCompileCacheKey())
                && save.getCompiledHexBase64() != null) {
            log.info("Compile cache hit for save {} (key={})", save.getId(), cacheKey);
            return CompletableFuture.completedFuture(
                    CompileCircuitResponse.cachedSuccess(save.getCompiledHexBase64(), fqbn, definition.getBoardType()));
        }

        // Mark as compiling
        save.setSimulationStatus(SimulationStatus.COMPILING);
        save.setLastCompileError(null);
        saveRepository.save(save);

        // Fetch sketch source
        String sketchCode = giteaService.getFileContent(giteaService.getAdminUsername(), repoName, SKETCH_FILENAME);
        if (sketchCode == null) {
            return CompletableFuture.completedFuture(
                    failSave(save, "sketch.ino not found in Gitea repository '" + repoName + "'."));
        }

        return CompletableFuture.completedFuture(
                doCompile(save, sketchCode, fqbn, definition.getBoardType(), cacheKey));
    }

    // --- Compilation ---

    private CompileCircuitResponse doCompile(CadetCircuitSave save, String sketchCode, String fqbn, BoardType boardType, String cacheKey) {
        Path tempRoot = null;
        try {
            // arduino-cli requires: <tempRoot>/sketch/sketch.ino
            tempRoot = Files.createTempDirectory("arduino-" + save.getId());
            Path sketchDir = tempRoot.resolve("sketch");
            Files.createDirectories(sketchDir);
            Files.writeString(sketchDir.resolve(SKETCH_FILENAME), sketchCode);

            Path outputDir = tempRoot.resolve("output");
            Files.createDirectories(outputDir);

            long startMs = System.currentTimeMillis();
            ArduinoCliRunner.Result result = cliRunner.run(fqbn, sketchDir, outputDir);
            long elapsedMs = System.currentTimeMillis() - startMs;

            if (!result.success()) {
                log.warn("Compile failed for save {}: {}", save.getId(), result.output());
                return failSave(save, result.output());
            }

            // arduino-cli names the hex: <outputDir>/sketch.ino.hex
            Path hexFile = outputDir.resolve(SKETCH_FILENAME + ".hex");
            if (!Files.exists(hexFile)) {
                // Some versions use <outputDir>/<board_fqbn_flat>/sketch.ino.hex
                hexFile = findHexFile(outputDir);
            }
            if (hexFile == null || !Files.exists(hexFile)) {
                return failSave(save, "Compilation succeeded but .hex file not found in output directory.");
            }

            byte[] hexBytes = Files.readAllBytes(hexFile);
            String hexBase64 = Base64.getEncoder().encodeToString(hexBytes);

            save.setSimulationStatus(SimulationStatus.NEVER_RUN); // ready to simulate, not yet running
            save.setCompilationTimeMs(elapsedMs);
            save.setSimulationStartedAt(null);
            // Store compiled hex in cache
            save.setCompiledHexBase64(hexBase64);
            save.setCompileCacheKey(cacheKey);
            saveRepository.save(save);

            log.info("Compile OK for save {} in {}ms, hex size {} bytes.", save.getId(), elapsedMs, hexBytes.length);
            return CompileCircuitResponse.success(hexBase64, fqbn, boardType, elapsedMs);

        } catch (IOException | InterruptedException e) {
            log.error("Compiler I/O error for save {}: {}", save.getId(), e.getMessage());
            Thread.currentThread().interrupt();
            return failSave(save, "Internal compiler error: " + e.getMessage());
        } finally {
            deleteTempDir(tempRoot);
        }
    }

    /** Searches recursively for any .hex file under outputDir. */
    private Path findHexFile(Path outputDir) throws IOException {
        try (Stream<Path> paths = Files.walk(outputDir)) {
            return paths
                    .filter(p -> p.toString().endsWith(".hex"))
                    .min(Comparator.comparingInt(p -> p.getNameCount()))
                    .orElse(null);
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

    private String buildCacheKey(String repoUrl, String commitHash, String fqbn) {
        if (repoUrl == null || commitHash == null || fqbn == null) return null;
        try {
            String input = repoUrl + "|" + commitHash + "|" + fqbn;
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

    private void deleteTempDir(Path dir) {
        if (dir == null) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            log.warn("Failed to clean temp dir {}: {}", dir, e.getMessage());
        }
    }
}
