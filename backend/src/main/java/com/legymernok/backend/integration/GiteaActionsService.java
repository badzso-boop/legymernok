package com.legymernok.backend.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages Arduino sketch compilation via Gitea Actions workflows.
 *
 * Flow:
 *  1. dispatchCompileWorkflow() — triggers the compile.yml workflow on the cadet's circuit repo
 *  2. pollUntilComplete()       — polls the Gitea Actions runs API until the job finishes
 *  3. downloadHexArtifact()     — downloads and unzips the .hex artifact uploaded by the workflow
 *
 * The workflow itself (compile.yml) lives in the mission-circuit-template repo and is
 * inherited by every cadet circuit repo created from it.
 */
@Service
@Slf4j
public class GiteaActionsService {

    private final RestClient restClient;
    private final String adminUsername;
    private final long pollIntervalMs;

    /** Result returned by pollUntilComplete once the workflow run finishes. */
    public record WorkflowRunResult(boolean success, String conclusion, long runId) {}

    public GiteaActionsService(
            @Value("${gitea.api.url}") String apiUrl,
            @Value("${gitea.admin.username}") String adminUsername,
            @Value("${gitea.admin.password}") String adminPassword,
            @Value("${arduino.compile.poll.interval-ms:3000}") long pollIntervalMs) {

        this.adminUsername = adminUsername;
        this.pollIntervalMs = pollIntervalMs;

        String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString((adminUsername + ":" + adminPassword).getBytes(StandardCharsets.UTF_8));

        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", basicAuth)
                .build();
    }

    /**
     * Triggers the compile.yml workflow_dispatch on the admin-owned cadet circuit repo.
     *
     * @return The Instant captured just before the API call — used to identify the resulting run.
     */
    public Instant dispatchCompileWorkflow(String repoName, String fqbn, UUID saveId) {
        Instant before = Instant.now();

        Map<String, Object> body = Map.of(
                "ref", "main",
                "inputs", Map.of(
                        "fqbn", fqbn,
                        "save_id", saveId.toString()
                )
        );

        restClient.post()
                .uri("/repos/{owner}/{repo}/actions/workflows/compile.yml/dispatches",
                        adminUsername, repoName)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("Dispatched compile workflow for repo '{}' (fqbn={}, saveId={})", repoName, fqbn, saveId);
        return before;
    }

    /**
     * Polls the Gitea Actions runs API until the workflow run (dispatched after {@code dispatchedAfter})
     * reaches status "completed", or the timeout is exceeded.
     *
     * @param timeoutSeconds Maximum total wait time.
     * @throws RuntimeException on timeout or if the polling thread is interrupted.
     */
    public WorkflowRunResult pollUntilComplete(String repoName, Instant dispatchedAfter, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + (long) timeoutSeconds * 1000;

        while (System.currentTimeMillis() < deadline) {
            sleep();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri("/repos/{owner}/{repo}/actions/runs?limit=10", adminUsername, repoName)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null) continue;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> runs = (List<Map<String, Object>>) response.get("workflow_runs");
            if (runs == null || runs.isEmpty()) continue;

            for (Map<String, Object> run : runs) {
                String createdAt = (String) run.get("created_at");
                if (createdAt == null) continue;

                Instant runCreated = Instant.parse(createdAt);
                if (runCreated.isBefore(dispatchedAfter)) continue; // older run, skip

                long runId = ((Number) run.get("id")).longValue();
                String status = (String) run.get("status");

                if ("completed".equals(status)) {
                    String conclusion = (String) run.get("conclusion");
                    boolean success = "success".equals(conclusion);
                    log.info("Workflow run {} completed: conclusion='{}' repo='{}'", runId, conclusion, repoName);
                    return new WorkflowRunResult(success, conclusion, runId);
                }

                // Run found but still in progress — wait for next poll cycle
                log.debug("Workflow run {} status='{}' for repo '{}', waiting...", runId, status, repoName);
                break;
            }
        }

        throw new RuntimeException(
                "Compile workflow timed out after " + timeoutSeconds + "s for repo '" + repoName + "'");
    }

    /**
     * Downloads the artifact named "hex-{saveId}" from the given workflow run,
     * extracts the first .hex file from the zip, and returns its raw bytes.
     */
    public byte[] downloadHexArtifact(String repoName, long runId, UUID saveId) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> artifactsResponse = restClient.get()
                .uri("/repos/{owner}/{repo}/actions/runs/{run_id}/artifacts",
                        adminUsername, repoName, runId)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (artifactsResponse == null) {
            throw new IOException("Empty artifacts response for run " + runId);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) artifactsResponse.get("artifacts");
        if (artifacts == null || artifacts.isEmpty()) {
            throw new IOException("No artifacts found for run " + runId);
        }

        String targetName = "hex-" + saveId;
        Map<String, Object> hexArtifact = artifacts.stream()
                .filter(a -> targetName.equals(a.get("name")))
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "Artifact '" + targetName + "' not found in run " + runId));

        long artifactId = ((Number) hexArtifact.get("id")).longValue();

        byte[] zipBytes = restClient.get()
                .uri("/repos/{owner}/{repo}/actions/artifacts/{artifact_id}/zip",
                        adminUsername, repoName, artifactId)
                .retrieve()
                .body(byte[].class);

        if (zipBytes == null || zipBytes.length == 0) {
            throw new IOException("Artifact zip is empty for artifact " + artifactId);
        }

        // Extract the .hex file from the zip
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".hex")) {
                    return zis.readAllBytes();
                }
            }
        }
        throw new IOException("No .hex file inside artifact zip for run " + runId);
    }

    private void sleep() {
        try {
            Thread.sleep(pollIntervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Compile poll interrupted", e);
        }
    }
}
