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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Compiles an Arduino sketch stored in the cadet's Gitea repo.
 *
 * Flow:
 *  1. Fetch sketch.ino from Gitea
 *  2. Write to a temp directory (arduino-cli requires dir name == sketch name)
 *  3. Run: arduino-cli compile --fqbn <fqbn> --output-dir <outputDir> <sketchDir>
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

    @Value("${arduino.cli.path:arduino-cli}")
    private String arduinoCliPath;

    @Value("${arduino.cli.compile.timeout-seconds:120}")
    private int compileTimeoutSeconds;

    // --- Public API ---

    /**
     * Compiles the sketch for the given cadet + mission.
     * Updates simulationStatus on the CadetCircuitSave.
     */
    @Transactional
    public CompileCircuitResponse compile(String username, UUID missionId) {
        // Resolve cadet
        var cadet = cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", username));

        // Resolve published definition
        var definition = circuitDefinitionRepository
                .findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("CircuitDefinition", "missionId/status", missionId + "/PUBLISHED"));

        // Resolve save
        CadetCircuitSave save = saveRepository
                .findByCadetIdAndCircuitDefinitionId(cadet.getId(), definition.getId())
                .orElseThrow(() -> new ResourceNotFoundException("CadetCircuitSave", "username/missionId", username + "/" + missionId));

        if (save.getGiteaRepoUrl() == null) {
            return failSave(save, "Circuit mission not properly started: missing Gitea repo URL.");
        }

        // Mark as compiling
        save.setSimulationStatus(SimulationStatus.COMPILING);
        save.setLastCompileError(null);
        saveRepository.save(save);

        // Fetch sketch source
        String repoName = extractRepoName(save.getGiteaRepoUrl());
        String sketchCode = giteaService.getFileContent(giteaService.getAdminUsername(), repoName, SKETCH_FILENAME);
        if (sketchCode == null) {
            return failSave(save, "sketch.ino not found in Gitea repository '" + repoName + "'.");
        }

        // Compile
        String fqbn = toFqbn(definition.getBoardType());
        return doCompile(save, sketchCode, fqbn, definition.getBoardType());
    }

    // --- Compilation ---

    private CompileCircuitResponse doCompile(CadetCircuitSave save, String sketchCode, String fqbn, BoardType boardType) {
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
            ProcessResult result = runArduinoCli(fqbn, sketchDir, outputDir);
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

    private ProcessResult runArduinoCli(String fqbn, Path sketchDir, Path outputDir)
            throws IOException, InterruptedException {

        List<String> command = List.of(
                arduinoCliPath, "compile",
                "--fqbn", fqbn,
                "--output-dir", outputDir.toAbsolutePath().toString(),
                sketchDir.toAbsolutePath().toString()
        );
        log.debug("Running: {}", String.join(" ", command));

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        boolean finished = process.waitFor(compileTimeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult(false, "Compilation timed out after " + compileTimeoutSeconds + " seconds.");
        }

        String output = new String(process.getInputStream().readAllBytes());
        boolean success = process.exitValue() == 0;
        return new ProcessResult(success, output);
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

    /**
     * Maps BoardType to the arduino-cli FQBN string.
     * P1: AVR boards. P2: ESP (requires additional core config).
     */
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
     * Extracts the Gitea repo name from the clone URL.
     * E.g. "http://gitea:3000/legymernok_admin/circuit-abc-user" → "circuit-abc-user"
     */
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

    private record ProcessResult(boolean success, String output) {}
}
