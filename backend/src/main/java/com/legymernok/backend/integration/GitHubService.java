package com.legymernok.backend.integration;

import com.legymernok.backend.exception.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

// A visszajelzés/feature-kérés tábla GitHub Issues-alapú tárolója. Minden
// beküldött visszajelzés egy valódi Issue lesz a repóban ("feedback"
// címkével), a lista pedig ugyanezt olvassa vissza — nincs saját DB tábla,
// a GitHub Issues MAGA a tároló, ahogy a repóban dolgozók amúgy is látják
// a visszajelzéseket.
@Service
@Slf4j
public class GitHubService {

    private final RestClient restClient;
    private final String owner;
    private final String repo;
    private final String feedbackLabel;
    private final boolean configured;

    public GitHubService(
            @Value("${github.api.url}") String apiUrl,
            @Value("${github.token}") String token,
            @Value("${github.repo.owner}") String owner,
            @Value("${github.repo.name}") String repo,
            @Value("${github.feedback.label}") String feedbackLabel) {
        this.owner = owner;
        this.repo = repo;
        this.feedbackLabel = feedbackLabel;
        this.configured = token != null && !token.isBlank();

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                // A GitHub API minden kérésnél megköveteli a User-Agent headert.
                .defaultHeader("User-Agent", "legymernok-backend");
        if (configured) {
            builder = builder.defaultHeader("Authorization", "Bearer " + token);
        }
        this.restClient = builder.build();

        if (!configured) {
            log.warn("GITHUB_TOKEN nincs beállítva — a visszajelzés funkció hívásra ExternalServiceException-t fog dobni.");
        }
    }

    private void requireConfigured() {
        if (!configured) {
            throw new ExternalServiceException("GitHub",
                    "A visszajelzés funkció jelenleg nincs beállítva ezen a szerveren (hiányzó GITHUB_TOKEN).");
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createIssue(String title, String body) {
        requireConfigured();
        Map<String, Object> requestBody = Map.of(
                "title", title,
                "body", body,
                "labels", List.of(feedbackLabel)
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/repos/{owner}/{repo}/issues", owner, repo)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("number")) {
                throw new ExternalServiceException("GitHub", "Nem sikerült létrehozni az issue-t: üres válasz.");
            }
            log.info("GitHub issue #{} létrehozva a {}/{} repóban.", response.get("number"), owner, repo);
            return response;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Nem sikerült GitHub issue-t létrehozni ({}/{}): {}", owner, repo, e.getMessage());
            throw new ExternalServiceException("GitHub", "Nem sikerült létrehozni az issue-t: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listFeedbackIssues() {
        requireConfigured();
        try {
            List<Map<String, Object>> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/issues")
                            .queryParam("labels", feedbackLabel)
                            .queryParam("state", "all")
                            .queryParam("sort", "created")
                            .queryParam("direction", "desc")
                            .queryParam("per_page", 50)
                            .build(owner, repo))
                    .retrieve()
                    .body(List.class);
            return response != null ? response : List.of();
        } catch (Exception e) {
            log.error("Nem sikerült lekérni a GitHub issue-kat ({}/{}): {}", owner, repo, e.getMessage());
            throw new ExternalServiceException("GitHub", "Nem sikerült lekérni a visszajelzéseket: " + e.getMessage());
        }
    }
}
