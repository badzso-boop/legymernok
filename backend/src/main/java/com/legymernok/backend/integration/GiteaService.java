package com.legymernok.backend.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class GiteaService {

    private final RestClient restClient;
    private final String adminUsername;
    private final String adminToken;

    public GiteaService(
            @Value("${gitea.api.url}") String apiUrl,
            @Value("${gitea.admin.username}") String adminUsername,
            @Value("${gitea.admin.password}") String adminPassword,
            @Value("${gitea.admin.token}") String adminToken) {

        this.adminUsername = adminUsername;
        this.adminToken = adminToken;

        String basicAuth = "Basic " + Base64.getEncoder().encodeToString((adminUsername + ":" + adminPassword).getBytes(StandardCharsets.UTF_8));

        // DEBUG LOG - Ezt később kivesszük
        System.out.println("--- GITEA SERVICE INIT ---");
        System.out.println("URL: " + apiUrl);
        System.out.println("User: " + adminUsername);
        System.out.println("Token (first 5 chars): " + (adminToken != null && adminToken.length() > 5 ? adminToken.substring(0, 5) : "null"));
        // ------------------------------------

        // Basic Auth beállítása az adminnak
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                //.defaultHeader("Authorization", "token " + adminToken)
                //.defaultHeaders(headers -> headers.setBasicAuth(adminUsername, adminPassword))
                .defaultHeader("Authorization", basicAuth)
                .build();
    }

    public Long createGiteaUser(String username, String email, String password) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("username", username);
        requestBody.put("email", email);
        requestBody.put("password", password);
        requestBody.put("login_name", username);
        requestBody.put("must_change_password", false);
        requestBody.put("send_notify", false);

        Map response = restClient.post()
                .uri("/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response != null && response.containsKey("id")) {
            return ((Number) response.get("id")).longValue();
        }

        throw new RuntimeException("Failed to create user in Gitea: no ID returned");
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
     *Létrehoz vagy felülír egy fájlt a repository-ban.
     *Kezeli a "file already exists" esetet (pl. README.md) update hívással.
     *
     * @param repoName A repository neve (ahová a fájlt tesszük).
     * @param filePath A fájl útvonala a repón belül (pl. "src/Main.java").
     * @param content  A fájl szöveges tartalma.
     */
    public void createFile(String repoName, String filePath, String content) {
        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("content", encodedContent);
        requestBody.put("message", "Commit for " + filePath);

        try {
            restClient.post()
                    .uri("/repos/{owner}/{repo}/contents/{filepath}", adminUsername, repoName,
                            filePath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (org.springframework.web.client.HttpClientErrorException.UnprocessableEntity e) {
            updateFile(repoName, filePath, requestBody);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Egyéb hiba esetén dobjuk tovább (pl. 404, 401)
            // Ha a 422 nem UnprocessableEntity-ként jön, hanem sima ClientError-ként, itt is elkaphatjuk
            if (e.getStatusCode().value() == 422) {
                updateFile(repoName, filePath, requestBody);
            } else {
                throw e;
            }
        }
    }

    /**
     * Segédmetódus egy fájl tartalmának felülírására egy Gitea repository-ban.
     * Az update művelethez szükséges a fájl aktuális SHA hash-e.
     */
    private void updateFile(String repoName, String filePath, Map<String, Object> requestBody) {
        Map fileInfo = restClient.get()
                .uri("/repos/{owner}/{repo}/contents/{filepath}", adminUsername, repoName, filePath)
                        .retrieve()
                        .body(Map.class);

        if (fileInfo != null && fileInfo.containsKey("sha")) {
            String sha = (String) fileInfo.get("sha");
            requestBody.put("sha", sha);
            requestBody.put("message", "Update " + filePath);

            restClient.put()
                    .uri("/repos/{owner}/{repo}/contents/{filepath}", adminUsername, repoName,
                            filePath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
        } else {
            throw new RuntimeException("Failed to retrieve SHA for existing file: " + filePath);
        }
    }

    /**
     * Töröl egy felhasználót a Gitea-ból.
     * FIGYELEM: Ez véglegesen törli a felhasználót és az általa birtokolt összes repository-t is!
     * @param username A törlendő felhasználó Gitea login neve.
     */
    public void deleteGiteaUser(String username) {
        restClient.delete()
                .uri("/admin/users/{username}", username)
                .retrieve()
                .toBodilessEntity(); // A 204 No Content választ várjuk
    }

    /**
     * Töröl egy repository-t a Gitea-ból.
     * @param ownerUsername A repository tulajdonosának Gitea login neve.
     * @param repoName A törlendő repository neve.
     */
    public void deleteRepository(String ownerUsername, String repoName) {
        restClient.delete()
                .uri("/repos/{owner}/{repo}", ownerUsername, repoName)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Lekérdez egy adott repository-t név alapján az admin felhasználó alatt.
     *
     * @param repoName A lekérdezendő repository neve.
     * @return Optional<Map<String, Object>> - A repository adatai, ha létezik.
     */
    public Optional<Map<String, Object>> getRepository(String repoName) {
        try {
            // API hívás: GET /repos/{owner}/{repo}
            Map<String, Object> response = restClient.get()
                    .uri("/repos/{owner}/{repo}", adminUsername, repoName)
                    .retrieve()
                    .body(Map.class);
            return Optional.ofNullable(response);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    /**
     * Lekérdezi az admin felhasználó összes repository-ját.
     *
     * @return List<Map<String, Object>> - Az összes repository listája.
     */
    public List<Map<String, Object>> getAllUserRepositories() {
        // API hívás: GET /user/repos
        // Figyelem: A Gitea API alapértelmezetten lapozza az eredményeket.
        // Itt most csak az első oldalt kérjük le, de élesben kezelni kell a lapozást!
        List<Map<String, Object>> response = restClient.get()
                .uri("/user/repos")
                .retrieve()
                .body(List.class);
        return response;
    }


    @lombok.Data
    public static class GiteaContent {
        private String name;
        private String path;
        private String type; // "file" vagy "dir"
        private String content; // Base64
        private String download_url;
    }

    /**
     * Lekéri egy mappa tartalmát (fájllista).
     */
    public List<GiteaContent> getRepoContents(String repoName, String path) {
        String uriPath = (path == null || path.isEmpty()) ? "" : "/" + path;
        try {
            return restClient.get()
                    .uri("/repos/{owner}/{repo}/contents{path}", adminUsername, repoName, uriPath)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<GiteaContent>>() {});
        } catch (Exception e) {
            return List.of(); // Ha üres vagy hiba van
        }
    }

    /**
     * Lekéri egy fájl tartalmát (Stringként, decode-olva).
     */
    public String getFileContent(String repoName, String filePath) {
        try {
            GiteaContent content = restClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", adminUsername, repoName, filePath)
                            .retrieve()
                            .body(GiteaContent.class);

            if (content != null && content.getContent() != null) {
                // A Gitea Base64-e néha tartalmaz sortörést, ki kell venni
                byte[] decodedBytes = Base64.getDecoder().decode(content.getContent().replaceAll(
                        "\\n", ""));
                return new String(decodedBytes);
            }
        } catch (Exception e) {
            // Logolhatnánk
        }
        return "";
    }

    /**
     * Hozzáad egy felhasználót (collaborator) egy repóhoz.
     */
    public void addCollaborator(String repoName, String username, String permission) {
        Map<String, String> body = new HashMap<>();
        body.put("permission", permission); // "read", "write", "admin"

        restClient.put()
                .uri("/repos/{owner}/{repo}/collaborators/{collaborator}", adminUsername, repoName,
                        username)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

}