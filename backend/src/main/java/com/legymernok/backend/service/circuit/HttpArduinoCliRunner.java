package com.legymernok.backend.service.circuit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/**
 * Production ArduinoCliRunner implementation that delegates compilation to a
 * dedicated Docker microservice (arduino-compiler container).
 *
 * The microservice runs arduino-cli in an isolated, resource-limited container
 * (mem_limit: 256m, cpus: 0.5) and exposes a simple HTTP API:
 *
 *   POST /compile
 *   Body: { "fqbn": "arduino:avr:uno", "sketchBase64": "<base64 of sketch.ino>" }
 *   Response: { "success": true, "hexBase64": "<base64 hex>", "output": "" }
 *            or { "success": false, "output": "<compiler errors>" }
 *
 * Active only in the "production" Spring profile.
 */
@Component
@Profile("production")
@Slf4j
public class HttpArduinoCliRunner implements ArduinoCliRunner {

    private final RestClient restClient;

    public HttpArduinoCliRunner(
            @Value("${arduino.compiler.service.url:http://arduino-compiler:8090}") String serviceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(serviceUrl)
                .build();
    }

    @Override
    public Result run(String fqbn, Path sketchDir, Path outputDir) throws IOException, InterruptedException {
        // Read the sketch source written by ArduinoCompilerService
        Path sketchFile = sketchDir.resolve("sketch.ino");
        byte[] sketchBytes = Files.readAllBytes(sketchFile);
        String sketchBase64 = Base64.getEncoder().encodeToString(sketchBytes);

        Map<String, String> requestBody = Map.of(
                "fqbn", fqbn,
                "sketchBase64", sketchBase64
        );

        log.debug("Sending compile request to arduino-compiler service (fqbn={})", fqbn);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/compile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return new Result(false, "arduino-compiler service returned empty response");
            }

            boolean success = Boolean.TRUE.equals(response.get("success"));
            String output = (String) response.getOrDefault("output", "");

            if (success) {
                // Write the returned hex into outputDir so ArduinoCompilerService can read it
                String hexBase64 = (String) response.get("hexBase64");
                if (hexBase64 != null) {
                    byte[] hexBytes = Base64.getDecoder().decode(hexBase64);
                    Files.write(outputDir.resolve("sketch.ino.hex"), hexBytes);
                }
            }

            return new Result(success, output);

        } catch (Exception e) {
            log.error("HTTP compile request failed: {}", e.getMessage());
            return new Result(false, "arduino-compiler service unreachable: " + e.getMessage());
        }
    }
}
