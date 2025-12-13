package com.legymernok.backend.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class GiteaService {

    private final RestClient restClient;
    private final String adminUsername;

    public GiteaService(
            @Value("${gitea.api.url}") String apiUrl,
            @Value("${gitea.admin.username}") String adminUsername,
            @Value("${gitea.admin.password}") String adminPassword) {

        this.adminUsername = adminUsername;

        // Basic Auth beállítása az adminnak
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeaders(headers -> headers.setBasicAuth(adminUsername, adminPassword))
                .build();
    }

    public Long createGiteaUser(String username, String email, String password) {
        // JSON body összeállítása
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("username", username);
        requestBody.put("email", email);
        requestBody.put("password", password);
        requestBody.put("login_name", username);
        requestBody.put("must_change_password", false);

        // API hívás: POST /admin/users
        Map response = restClient.post()
                .uri("/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        // Visszakapjuk a létrehozott user ID-ját
        if (response != null && response.containsKey("id")) {
            return ((Number) response.get("id")).longValue();
        }

        throw new RuntimeException("Failed to create user in Gitea: No ID returned");
    }

    /**
     * Létrehoz egy új repository-t az adminisztrátor felhasználó alatt.
     *
     * @param repoName A létrehozandó repository neve (pl. "mission-1-template").
     * @return A repository klónozási URL-je (clone_url).
     */
    public String createRepository(String repoName) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", repoName);
        requestBody.put("private", true);
        requestBody.put("auto_init", true);

        // API hívás: POST /user/repos (Az aktuálisan bejelentkezett usernek, azaz az adminnak hozza létre)
        Map response = restClient.post()
                .uri("/user/repos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response != null && response.containsKey("clone_url")) {
            return (String) response.get("clone_url");
        }

        throw new RuntimeException("Failed to create repository in Gitea: No clone URL returned");
    }

    /**
     * Létrehoz (vagy felülír) egy fájlt a repository-ban.
     *
     * @param repoName A repository neve (ahová a fájlt tesszük).
     * @param filePath A fájl útvonala a repón belül (pl. "src/Main.java").
     * @param content  A fájl szöveges tartalma.
     */
    public void createFile(String repoName, String filePath, String content) {
        // Gitea admin username-re szükségünk van az URL összerakásához
        // Ezt vagy paraméterként kapjuk, vagy mezőként tároljuk.
        // A konstruktorban már megkaptuk a `adminUsername`-t, de lokális változó volt.
        // JAVASLAT: Tedd a `adminUsername`-t osztályszintű mezővé (lásd lentebb)!

        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("content", encodedContent);
        requestBody.put("message", "Initial commit for " + filePath); // Commit üzenet

        // Mivel auto_init=true-val hoztuk létre, a default branch 'main' vagy 'master' lesz.
        // Ha nem adjuk meg a branch-et, a defaultra megy.

        // API hívás: POST /repos/{owner}/{repo}/contents/{filepath}
        restClient.post()
                .uri("/repos/{owner}/{repo}/contents/{filepath}", adminUsername, repoName, filePath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toBodilessEntity(); // Nem érdekel a válasz body, csak hogy sikeres (201) legyen
    }
}