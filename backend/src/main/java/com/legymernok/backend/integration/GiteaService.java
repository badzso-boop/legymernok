package com.legymernok.backend.integration;

import com.legymernok.backend.exception.ExternalServiceException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.MissionType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
public class GiteaService {

    private final RestClient restClient;
    /**
     * -- GETTER --
     *  Visszaadja a Gitea adminisztrátor felhasználónevét.
     *
     * @return Az admin felhasználónév.
     */
    @Getter
    private final String adminUsername;
    private final String adminToken;
    // Template repo konfigurációk
    private final String jsTemplateRepoOwner;
    private final String jsTemplateRepoName;
    private final String pythonTemplateRepoOwner;
    private final String pythonTemplateRepoName;
    private final String quizTemplateRepoOwner;
    private final String quizTemplateRepoName;
    private final String verificationSecretValue;

    public GiteaService(
            @Value("${gitea.api.url}") String apiUrl,
            @Value("${gitea.admin.username}") String adminUsername,
            @Value("${gitea.admin.password}") String adminPassword,
            @Value("${gitea.admin.token}") String adminToken,
            @Value("${gitea.template.js.owner}") String jsTemplateRepoOwner,
            @Value("${gitea.template.js.repo}") String jsTemplateRepoName,
            @Value("${gitea.template.python.owner}") String pythonTemplateRepoOwner,
            @Value("${gitea.template.python.repo}") String pythonTemplateRepoName,
            @Value("${gitea.template.quiz.owner}") String quizTemplateRepoOwner,
            @Value("${gitea.template.quiz.repo}")String quizTemplateRepoName,
            @Value("${mission.verification.secret}") String verificationSecretValue) {

        this.adminUsername = adminUsername;
        this.adminToken = adminToken;
        this.jsTemplateRepoOwner = jsTemplateRepoOwner;
        this.jsTemplateRepoName = jsTemplateRepoName;
        this.pythonTemplateRepoOwner = pythonTemplateRepoOwner;
        this.pythonTemplateRepoName = pythonTemplateRepoName;
        this.quizTemplateRepoOwner = quizTemplateRepoOwner;
        this.quizTemplateRepoName = quizTemplateRepoName;
        this.verificationSecretValue = verificationSecretValue;

        String basicAuth = "Basic " + Base64.getEncoder().encodeToString((adminUsername + ":" + adminPassword).getBytes(StandardCharsets.UTF_8));

        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                //.defaultHeader("Authorization", "token " + adminToken)
                //.defaultHeaders(headers -> headers.setBasicAuth(adminUsername, adminPassword))
                .defaultHeader("Authorization", basicAuth)
                .build();
    }

    /**
     * Létrehoz egy Gitea felhasználói fiókot az admin jogokkal.
     * @param username A létrehozandó felhasználó neve.
     * @param email A felhasználó email címe.
     * @param password A felhasználó jelszava.
     * @return A Gitea felhasználó ID-je.
     * @throws ExternalServiceException Ha hiba történik (pl. már létező felhasználó, vagy API hiba).
     */
    public Long createGiteaUser(String username, String email, String password) {
        log.info("Attempting to create Gitea user: {}", username);
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("username", username);
        requestBody.put("email", email);
        requestBody.put("password", password);
        requestBody.put("login_name", username);
        requestBody.put("must_change_password", false);
        requestBody.put("send_notify", false);

        try {
            Map response = restClient.post()
                    .uri("/admin/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("id")) {
                Long giteaId = ((Number) response.get("id")).longValue();
                log.info("Successfully created Gitea user: {} with ID: {}", username, giteaId);
                return giteaId;
            }
        } catch (HttpClientErrorException.Conflict e) {
            log.warn("Gitea user '{}' already exists. Conflict: {}", username, e.getMessage());
            throw new ExternalServiceException("Gitea", "User '" + username + "' already exists.");
        } catch (Exception e) {
            log.error("Failed to create Gitea user '{}'. Error: {}", username, e.getMessage());
            throw new ExternalServiceException("Gitea", "Failed to create user: " + e.getMessage());
        }
        throw new ExternalServiceException("Gitea", "Failed to create user: no ID returned.");
    }

    /**
     * Töröl egy felhasználót a Gitea-ból.
     * FIGYELEM: Ez véglegesen törli a felhasználót és az általa birtokolt összes repository-t is!
     * @param username A törlendő felhasználó Gitea login neve.
     * @throws ExternalServiceException Ha hiba történik (pl. felhasználó nem található).
     */
    public void deleteGiteaUser(String username) {
        log.info("Attempting to delete Gitea user: {}", username);
        try {
            restClient.delete()
                    .uri("/admin/users/{username}", username)
                    .retrieve()
                    .toBodilessEntity(); // A 204 No Content választ várjuk
            log.info("Successfully deleted Gitea user: {}", username);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Gitea user '{}' not found, skipping deletion. Error: {}", username, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete Gitea user '{}'. Error: {}", username, e.getMessage());
            throw new ExternalServiceException("Gitea", "Failed to delete user: " + e.getMessage());
        }
    }

    /**
     * Létrehoz egy új, üres repository-t az adminisztrátor felhasználó alatt.
     *
     * @param repoName Az létrehozandó repository neve (pl. "mission-1-template").
     * @param isPrivate A repository legyen-e privát.
     * @return Az új repository klónozási URL-je (clone_url).
     */
    public String createEmptyRepository(String repoName, boolean isPrivate) {
        log.info("Attempting to create empty Gitea repository '{}' as admin.", repoName);
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", repoName);
        requestBody.put("private", isPrivate);
        requestBody.put("auto_init", false);
        // description, license is beállítható

        try {
            Map response = restClient.post()
                    .uri("/user/repos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("clone_url")) {
                String cloneUrl = (String) response.get("clone_url");
                log.info("Successfully created Gitea repository '{}'. URL: {}", repoName, cloneUrl);
                return cloneUrl;
            }
        } catch (HttpClientErrorException.Conflict e) {
            log.warn("Gitea repository '{}' already exists. Conflict: {}", repoName, e.getMessage());
            throw new ExternalServiceException("Gitea", "Repository '" + repoName + "' already exists.");
        } catch (Exception e) {
            log.error("Failed to create Gitea repository '{}'. Error: {}", repoName, e.getMessage());
            throw new ExternalServiceException("Gitea", "Failed to create repository: " + e.getMessage());
        }
        throw new ExternalServiceException("Gitea", "Failed to create repository: No clone URL returned.");
    }

    /**
     * Töröl egy repository-t a Gitea-ból.
     * @param owner A repository tulajdonosának neve.
     * @param repoName A törlendő repository neve.
     * @throws ExternalServiceException Ha hiba történik (pl. repó nem található, vagy jogosultság hiánya).
     */
    public void deleteRepository(String owner, String repoName) {
        log.info("Attempting to delete Gitea repository: {}/{}", owner, repoName);
        try {
            restClient.delete()
                    .uri("/repos/{owner}/{repo}", owner, repoName)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully deleted Gitea repository: {}/{}", owner, repoName);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Gitea repository '{}/{}' not found, skipping deletion. Error: {}", owner, repoName, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete Gitea repository '{}/{}'. Error: {}", owner, repoName, e.getMessage());
            throw new ExternalServiceException("Gitea", "Failed to delete repository: " + e.getMessage());
        }
    }

    /**
     * Töröl egy, az admin felhasználóhoz tartozó repository-t.
     * @param repoName A törlendő repository neve.
     * @throws ExternalServiceException Ha hiba történik.
     */
    public void deleteAdminRepository(String repoName) {
        deleteRepository(this.adminUsername, repoName);
    }

    /**
     * Rekurzívan másolja egy repository tartalmát (fájlok és mappák) egy másik repository-ba.
     * Mindkét repository-nak az admin tulajdonában kell lennie a művelethez.
     *
     * @param sourceOwner    A forrás repository tulajdonosának neve.
     * @param sourceRepoName A forrás repository neve.
     * @param targetRepoName A cél repository neve (az admin alatt).
     * @throws ExternalServiceException Ha hiba történik a másolás során.
     */
    public void copyRepositoryContents(String sourceOwner, String sourceRepoName, String targetRepoName) {
        log.info("Collecting contents from {}/{} to copy to admin's {}", sourceOwner, sourceRepoName, targetRepoName);
        Map<String, String> allFiles = new HashMap<>();
        collectFilesRecursive(sourceOwner, sourceRepoName, "", allFiles);

        // Egyetlen nagy commit az összes fájllal
        uploadFiles(adminUsername, targetRepoName, allFiles, "Initial template copy", null);
    }

    private void collectFilesRecursive(String owner, String repoName, String path, Map<String, String> collection) {
        List<GiteaContent> contents = getRepoContents(owner, repoName, path);
        for (GiteaContent content : contents) {
            if ("file".equals(content.getType())) {
                String fileContent = getFileContent(owner, repoName, content.getPath());
                if (fileContent != null) {
                    collection.put(content.getPath(), fileContent);
                }
            } else if ("dir".equals(content.getType())) {
                collectFilesRecursive(owner, repoName, content.getPath(), collection);
            }
        }
    }

    public String uploadFile(String repoOwner, String repoName, String filePath, String content) {
        return uploadFile(repoOwner, repoName, filePath, content, null);
    }

    /**
     * Feltölt több fájlt egyetlen commit-ban.
     * @param files Egy Map, ahol a kulcs a fájl útvonala, az érték a tartalom.
     * @param "operation" "create" vagy "update" (Gitea API-tól függően).
     */
    public void uploadFiles(String repoOwner, String repoName, Map<String, String> files, String commitMessage, Cadet user) {
        if (files == null || files.isEmpty()) return;

        // 1. Lekérjük a jelenlegi fájlokat, hogy tudjuk az SHA-kat (ha léteznek)
        // Ez fontos, mert a Batch API nem tud "upsert"-et (create or update), nekünk kell megmondani
        Map<String, String> currentShas = new HashMap<>();
        try {
            // Végigmegyünk a gyökérkönyvtáron (és rekurzívan ha kell, de egyszerűség kedvéért a főbb fájlokra)
            List<GiteaContent> contents = getRepoContents(repoOwner, repoName, "");
            for (GiteaContent c : contents) {
                if ("file".equals(c.getType())) currentShas.put(c.getPath(), c.getSha());
            }
        } catch (Exception e) {
            log.warn("Could not fetch current SHAs, assuming new repository.");
        }

        List<Map<String, Object>> fileActions = new ArrayList<>();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String path = entry.getKey();
            validateFilePath(path);
            String content = Base64.getEncoder().encodeToString(entry.getValue().getBytes(StandardCharsets.UTF_8));

            Map<String, Object> action = new HashMap<>();
            action.put("path", path);
            action.put("content", content);

            // Ha van már ilyen fájl, UPDATE + SHA, ha nincs, CREATE
            if (currentShas.containsKey(path)) {
                action.put("operation", "update");
                action.put("sha", currentShas.get(path));
            } else {
                action.put("operation", "create");
            }
            fileActions.add(action);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("files", fileActions);
        requestBody.put("message", commitMessage);
        requestBody.put("branch", "main"); // Mindig a main-re commitolunk

        // Szerző beállítása...
        String authorName = (user != null) ? user.getUsername() : adminUsername;
        String authorEmail = (user != null && user.getEmail() != null) ? user.getEmail() : adminUsername + "@legymernok.hu";
        Map<String, String> identity = new HashMap<>();
        identity.put("name", authorName);
        identity.put("email", authorEmail);
        requestBody.put("author", identity);
        requestBody.put("committer", identity);

        log.info("Request body: {}", requestBody);

        try {
            restClient.post()
                    .uri("/repos/{owner}/{repo}/contents", repoOwner, repoName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully batch committed {} files to {}/{}", files.size(), repoOwner, repoName);
        } catch (Exception e) {
            log.error("Batch upload failed: {}", e.getMessage());
            throw new ExternalServiceException("Gitea", "Batch upload failed: " + e.getMessage());
        }
    }

    /**
     * Feltölt vagy frissít egy fájlt a megadott repository-ban.
     * Ha a fájl létezik, frissíti; ha nem, létrehozza.
     * @param repoOwner A repository tulajdonosának neve.
     * @param repoName A repository neve.
     * @param filePath A fájl útvonala a repón belül.
     * @param content A feltöltendő tartalom stringként.
     * @return A fájl URL-je.
     * @throws ExternalServiceException Ha hiba történik a művelet során.
     */
    public String uploadFile(String repoOwner, String repoName, String filePath, String content, Cadet user) {
        validateFilePath(filePath);
        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));

        String now = OffsetDateTime.now().toString();
        String commitMessage = "Update " + filePath;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("content", encodedContent);
        requestBody.put("message", commitMessage);

        String authorName = (user != null) ? user.getUsername() : adminUsername;
        String authorEmail = (user != null && user.getEmail() != null) ? user.getEmail() : adminUsername + "@legymernok.hu";

        Map<String, String> identity = new HashMap<>();
        identity.put("name", authorName);
        identity.put("email", authorEmail);

        requestBody.put("author", identity);
        requestBody.put("committer", identity);

        Map<String, String> dates = new HashMap<>();
        dates.put("author", now);
        dates.put("committer", now);
        requestBody.put("dates", dates);

        String uri = String.format("/repos/%s/%s/contents/%s", repoOwner, repoName, filePath);

        try {
            Map<String, Object> fileInfo = getFileInfo(repoOwner, repoName, filePath);

            if (fileInfo != null && fileInfo.containsKey("sha")) {
                // Frissítés (PUT)
                requestBody.put("sha", fileInfo.get("sha"));
                log.info("Updating file: {}", filePath);
                Map response = restClient.put()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(Map.class);
                return (String) response.get("html_url");
            } else {
                // Létrehozás (POST)
                log.info("Creating file: {}", filePath);
                Map response = restClient.post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(Map.class);
                return (String) response.get("html_url");
            }
        } catch (Exception e) {
            log.error("Failed to upload file {} to {}/{}. Error: {}", filePath, repoOwner, repoName, e.getMessage());
            throw new ExternalServiceException("Gitea", "Failed to upload file: " + e.getMessage());
        }
    }

    private Map<String, Object> getFileInfo(String owner, String repoName, String filePath) {
        try {
            // Itt is fontos a közvetlen URI összefűzés
            String uri = String.format("/repos/%s/%s/contents/%s", owner, repoName, filePath);
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (HttpClientErrorException.NotFound e) {
            return null; // Ez így már jó lesz az uploadFile if-ágához
        } catch (Exception e) {
            log.error("Error getting file info for {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    /**
     * Töröl egy fájlt a megadott repository-ban.
     * @param repoOwner A repository tulajdonosának neve.
     * @param repoName A repository neve.
     * @param filePath A törlendő fájl útvonala a repón belül.
     * @param user A műveletet végző Cadet (commit author), vagy null (admin).
     * @throws ExternalServiceException Ha a fájl nem létezik, vagy a törlés sikertelen.
     */
    public void deleteFile(String repoOwner, String repoName, String filePath, Cadet user) {
        validateFilePath(filePath);
        Map<String, Object> fileInfo = getFileInfo(repoOwner, repoName, filePath);
        if (fileInfo == null || !fileInfo.containsKey("sha")) {
            throw new ExternalServiceException("Gitea", "File not found, cannot delete: " + filePath);
        }

        String authorName = (user != null) ? user.getUsername() : adminUsername;
        String authorEmail = (user != null && user.getEmail() != null) ? user.getEmail() : adminUsername + "@legymernok.hu";
        Map<String, String> identity = new HashMap<>();
        identity.put("name", authorName);
        identity.put("email", authorEmail);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sha", fileInfo.get("sha"));
        requestBody.put("message", "Delete " + filePath);
        requestBody.put("author", identity);
        requestBody.put("committer", identity);

        String uri = String.format("/repos/%s/%s/contents/%s", repoOwner, repoName, filePath);
        try {
            restClient.method(org.springframework.http.HttpMethod.DELETE)
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Deleted file {} from {}/{}", filePath, repoOwner, repoName);
        } catch (Exception e) {
            log.error("Failed to delete file {} from {}/{}. Error: {}", filePath, repoOwner, repoName, e.getMessage());
            throw new ExternalServiceException("Gitea", "Failed to delete file: " + e.getMessage());
        }
    }

    /**
     * Átnevez (áthelyez) egy fájlt a repositoryn belül. A Gitea contents API-nak
     * nincs natív "rename" művelete, ezért ez összetett: lekéri a jelenlegi
     * tartalmat, létrehozza az új útvonalon, majd törli a régit.
     * @param repoOwner A repository tulajdonosának neve.
     * @param repoName A repository neve.
     * @param oldPath A fájl jelenlegi útvonala.
     * @param newPath A fájl új útvonala.
     * @param user A műveletet végző Cadet (commit author), vagy null (admin).
     */
    public void renameFile(String repoOwner, String repoName, String oldPath, String newPath, Cadet user) {
        validateFilePath(oldPath);
        validateFilePath(newPath);
        if (oldPath.equals(newPath)) return;

        Map<String, Object> existingAtNewPath = getFileInfo(repoOwner, repoName, newPath);
        if (existingAtNewPath != null) {
            throw new ExternalServiceException("Gitea", "A file already exists at the target path: " + newPath);
        }

        String content = getFileContent(repoOwner, repoName, oldPath);
        if (content == null) {
            throw new ExternalServiceException("Gitea", "File not found, cannot rename: " + oldPath);
        }

        uploadFile(repoOwner, repoName, newPath, content, user);
        deleteFile(repoOwner, repoName, oldPath, user);
        log.info("Renamed file {} -> {} in {}/{}", oldPath, newPath, repoOwner, repoName);
    }

    public void setRepositorySecret(String repoName, String secretName, String secretValue) {
        Map<String, String> body = new HashMap<>();
        body.put("data", secretValue);

        // Gitea API végpont a secret beállításához
        restClient.put()
                .uri("/repos/{owner}/{repo}/actions/secrets/{secret_name}", adminUsername, repoName, secretName)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Lekér egy mappa tartalmát (fájllista).
     * @param owner A repository tulajdonosának felhasználóneve.
     * @param repoName A repository neve.
     * @param path A mappa útvonala a repón belül.
     * @return A mappa tartalma.
     */
    public List<GiteaContent> getRepoContents(String owner, String repoName, String path) {
        String uri = String.format("/repos/%s/%s/contents%s",
                owner,
                repoName,
                (path == null || path.isEmpty()) ? "" : "/" + path);
        try {
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<GiteaContent>>() {});
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Directory not found in Gitea: {}/{}/{}", owner, repoName, path);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get repo contents for {}/{}/{}: {}", owner, repoName, path, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Lekéri egy fájl tartalmát (Stringként, decode-olva).
     * @param owner A repository tulajdonosának felhasználóneve.
     * @param repoName A repository neve.
     * @param filePath A fájl útvonala.
     */
    public String getFileContent(String owner, String repoName, String filePath) {
        String uri = String.format("/repos/%s/%s/contents/%s", owner, repoName, filePath);
        try {
            GiteaContent content = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(GiteaContent.class);

            if (content != null && content.getContent() != null) {
                byte[] decodedBytes = Base64.getDecoder().decode(content.getContent().replaceAll("\\s", ""));
                return new String(decodedBytes);
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("File not found in Gitea: {}/{}/{}", owner, repoName, filePath);
            return null;
        } catch (Exception e) {
            log.error("Failed to get file content for {}/{}/{}: {}", owner, repoName, filePath, e.getMessage());
        }
        return null;
    }

    /**
     * Létrehoz egy új mission repository-t egy template alapján, és hozzáadja a usert kollaborátorként.
     * A repository az admin tulajdonában marad.
     *
     * @param missionIdString Az új repository neve (ajánlott, hogy ez legyen a Mission UUID string formában).
     * @param templateLanguage A template nyelve (pl. "javascript", "python").
     * @param user             A Cadet objektum, aki a repóhoz hozzáférést kap.
     * @return Az új repository klónozási URL-je.
     * @throws ExternalServiceException Ha hiba történik a Gitea műveletek során.
     */
    public String createMissionRepository(String missionIdString, String templateLanguage, Cadet user, MissionType type) {
        log.info("Creating mission repository for user '{}' from '{}' template.", user.getUsername(), templateLanguage);

        String sourceOwner;
        String sourceRepoName;
        String newRepoName = missionIdString;

        if (type == MissionType.QUIZ) {
            sourceOwner = quizTemplateRepoOwner;
            sourceRepoName = quizTemplateRepoName;
        } else if ("javascript".equalsIgnoreCase(templateLanguage)) {
            sourceOwner = jsTemplateRepoOwner;
            sourceRepoName = jsTemplateRepoName;
        } else if ("python".equalsIgnoreCase(templateLanguage)) {
            sourceOwner = pythonTemplateRepoOwner;
            sourceRepoName = pythonTemplateRepoName;
        } else {
            throw new IllegalArgumentException("Unsupported template language: " + templateLanguage);
        }

        // 1. Üres repó létrehozása az admin alatt
        String newRepoCloneUrl = createEmptyRepository(newRepoName, true);

        setRepositorySecret(newRepoName, "MISSION_VERIFICATION_SECRET", this.verificationSecretValue);

        // 2. Template tartalmának másolása az új repóba
        copyRepositoryContents(sourceOwner, sourceRepoName, newRepoName);

        // 3. User hozzáadása kollaborátorként 'write' joggal
        addCollaborator(newRepoName, user.getUsername(), "write");
        log.info("Mission repository '{}' created and user '{}' added as collaborator.", newRepoName, user.getUsername());

        return newRepoCloneUrl;
    }

    /**
     * Lekér egy repository-t név alapján egy adott tulajdonos alatt.
     * @param owner A repository tulajdonosának neve.
     * @param repoName A lekérdezendő repository neve.
     * @return Optional<Map<String, Object>> - A repository adatai, ha létezik.
     * @throws ExternalServiceException Ha API hiba történik (kivéve 404).
     */
    public Optional<Map<String, Object>> getRepository(String owner, String repoName) {
        log.debug("Attempting to get Gitea repository: {}/{}", owner, repoName);
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/repos/{owner}/{repo}", owner, repoName)
                    .retrieve()
                    .body(Map.class);
            return Optional.ofNullable(response);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty(); // Nem talált repository-t
        } catch (Exception e) {
            log.error("Failed to get Gitea repository '{}/{}'. Error: {}", owner, repoName, e.getMessage());
            throw new ExternalServiceException("Gitea", "Failed to get repository: " + e.getMessage());
        }
    }


    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class GiteaContent {
        private String name;
        private String path;
        private String sha;
        private String type; // "file" vagy "dir"
        private String content; // Base64
        private String download_url;
    }

    /**
     * Validálja, hogy a fájl path nem tartalmaz path traversal kísérletet.
     * Csak alfanumerikus karaktereket, pontot, kötőjelet, aláhúzást és perjelet enged meg.
     */
    private void validateFilePath(String path) {
        if (path == null || path.isBlank()
                || path.contains("..")
                || path.startsWith("/")
                || !path.matches("[a-zA-Z0-9._\\-/]+")) {
            throw new IllegalArgumentException("Invalid file path: " + path);
        }
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