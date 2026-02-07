package com.legymernok.backend.integration;

import com.legymernok.backend.exception.ExternalServiceException;
import com.legymernok.backend.model.cadet.Cadet;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;

import java.nio.charset.StandardCharsets;
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

    public GiteaService(
            @Value("${gitea.api.url}") String apiUrl,
            @Value("${gitea.admin.username}") String adminUsername,
            @Value("${gitea.admin.password}") String adminPassword,
            @Value("${gitea.admin.token}") String adminToken,
            @Value("${gitea.template.js.owner}") String jsTemplateRepoOwner,
            @Value("${gitea.template.js.repo}") String jsTemplateRepoName,
            @Value("${gitea.template.python.owner}") String pythonTemplateRepoOwner,
            @Value("${gitea.template.python.repo}") String pythonTemplateRepoName) {

        this.adminUsername = adminUsername;
        this.adminToken = adminToken;
        this.jsTemplateRepoOwner = jsTemplateRepoOwner;
        this.jsTemplateRepoName = jsTemplateRepoName;
        this.pythonTemplateRepoOwner = pythonTemplateRepoOwner;
        this.pythonTemplateRepoName = pythonTemplateRepoName;

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
        log.info("Copying contents from {}/{} to admin's {}", sourceOwner, sourceRepoName, targetRepoName);
        try {
            List<GiteaContent> contents = getRepoContents(sourceOwner, sourceRepoName, "");

            for (GiteaContent content : contents) {
                if ("file".equals(content.getType())) {
                    String fileContent = getFileContent(sourceOwner, sourceRepoName, content.getPath());
                    if (fileContent != null) {
                        uploadFile(adminUsername, targetRepoName, content.getPath(), fileContent);
                    }
                } else if ("dir".equals(content.getType())) {
                    copyDirectory(sourceOwner, sourceRepoName, targetRepoName, content.getPath());
                }
            }
            log.info("Successfully copied contents from {}/{} to admin's {}.", sourceOwner, sourceRepoName, targetRepoName);
        } catch (Exception e) {
            log.error("Failed to copy repository contents from {}/{} to {}. Error: {}", sourceOwner, sourceRepoName, targetRepoName, e.getMessage());
            throw new ExternalServiceException("Gitea", "Failed to copy repository contents: " + e.getMessage());
        }
    }

    /**
     * Segédmetódus mappák rekurzív másolására.
     */
    private void copyDirectory(String sourceOwner, String sourceRepoName, String targetRepoName, String currentPath) {
        List<GiteaContent> contents = getRepoContents(sourceOwner, sourceRepoName, currentPath);
        for (GiteaContent content : contents) {
            if ("file".equals(content.getType())) {
                String fileContent = getFileContent(sourceOwner, sourceRepoName, content.getPath());
                if (fileContent != null) {
                    uploadFile(adminUsername, targetRepoName, content.getPath(), fileContent);
                }
            } else if ("dir".equals(content.getType())) {
                copyDirectory(sourceOwner, sourceRepoName, targetRepoName, content.getPath()); // Rekurzió
            }
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
    public String uploadFile(String repoOwner, String repoName, String filePath, String content) {
        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        String commitMessage = "Update " + filePath;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("content", encodedContent);
        requestBody.put("message", commitMessage);

        try {
            Map<String, Object> fileInfo = getFileInfo(repoOwner, repoName, filePath);
            if (fileInfo != null && fileInfo.containsKey("sha")) {
                requestBody.put("sha", fileInfo.get("sha"));

                log.info("Updating file {} in {}/{}", filePath, repoOwner, repoName);
                Map response = restClient.put()
                        .uri("/repos/{owner}/{repo}/contents/{filepath}", repoOwner, repoName, filePath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(Map.class);
                return (String) response.get("html_url");
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.info("Creating file {} in {}/{}", filePath, repoOwner, repoName);
            Map response = restClient.post()
                    .uri("/repos/{owner}/{repo}/contents/{filepath}", repoOwner, repoName, filePath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
            return (String) response.get("html_url");
        } catch (Exception e) {
            log.error("Failed to upload file {} in {}/{}. Error: {}", filePath, repoOwner, repoName, e.getMessage());
            throw new ExternalServiceException("Gitea", "Failed to upload file: " + e.getMessage());
        }
        return null;
    }

    /**
     * Segédmetódus a fájl információinak lekéréséhez (SHA-érték miatt).
     */
    private Map<String, Object> getFileInfo(String owner, String repoName, String filePath) {
        try {
            return restClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{filepath}", owner, repoName, filePath)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.error("Failed to get file info for {}/{}/{}: {}", owner, repoName, filePath, e.getMessage());
            throw new ExternalServiceException("Gitea", "Failed to get file info: " + e.getMessage());
        }
    }

    /**
     * Lekér egy mappa tartalmát (fájllista).
     * @param owner A repository tulajdonosának felhasználóneve.
     * @param repoName A repository neve.
     * @param path A mappa útvonala a repón belül.
     * @return A mappa tartalma.
     */
    public List<GiteaContent> getRepoContents(String owner, String repoName, String path) {
        String uriPath = (path == null || path.isEmpty()) ? "" : "/" + path;
        try {
            return restClient.get()
                    .uri("/repos/{owner}/{repo}/contents{path}", owner, repoName, uriPath)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<GiteaContent>>() {});
        } catch (HttpClientErrorException.NotFound e) {
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
        try {
            GiteaContent content = restClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repoName, filePath)
                    .retrieve()
                    .body(GiteaContent.class);

            if (content != null && content.getContent() != null) {
                byte[] decodedBytes = Base64.getDecoder().decode(content.getContent().replaceAll("\\n", ""));
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
    public String createMissionRepository(String missionIdString, String templateLanguage, Cadet user) {
        log.info("Creating mission repository for user '{}' from '{}' template.", user.getUsername(), templateLanguage);

        String sourceOwner;
        String sourceRepoName;
        String newRepoName = missionIdString;

        if ("javascript".equalsIgnoreCase(templateLanguage)) {
            sourceOwner = jsTemplateRepoOwner;
            sourceRepoName = jsTemplateRepoName;
        } else if ("python".equalsIgnoreCase(templateLanguage)) {
            sourceOwner = pythonTemplateRepoOwner;
            sourceRepoName = pythonTemplateRepoName;
        } else {
            throw new IllegalArgumentException("Unsupported template language: " + templateLanguage);
        }

        // 1. Üres repó létrehozása az admin alatt
        String newRepoCloneUrl = createEmptyRepository(newRepoName, true); // Privát repó

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
    public static class GiteaContent {
        private String name;
        private String path;
        private String type; // "file" vagy "dir"
        private String content; // Base64
        private String download_url;
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