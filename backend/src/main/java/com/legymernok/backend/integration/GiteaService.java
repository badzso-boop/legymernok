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
     *  Returns the Gitea administrator username.
     *
     * @return The admin username.
     */
    @Getter
    private final String adminUsername;
    private final String adminToken;
    // Template repo configurations
    private final String jsTemplateRepoOwner;
    private final String jsTemplateRepoName;
    private final String pythonTemplateRepoOwner;
    private final String pythonTemplateRepoName;
    private final String quizTemplateRepoOwner;
    private final String quizTemplateRepoName;
    private final String circuitTemplateOwner;
    private final String circuitTemplateRepo;
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
            @Value("${gitea.template.quiz.repo}") String quizTemplateRepoName,
            @Value("${gitea.template.circuit.owner}") String circuitTemplateOwner,
            @Value("${gitea.template.circuit.repo}") String circuitTemplateRepo,
            @Value("${mission.verification.secret}") String verificationSecretValue) {

        this.adminUsername = adminUsername;
        this.adminToken = adminToken;
        this.jsTemplateRepoOwner = jsTemplateRepoOwner;
        this.jsTemplateRepoName = jsTemplateRepoName;
        this.pythonTemplateRepoOwner = pythonTemplateRepoOwner;
        this.pythonTemplateRepoName = pythonTemplateRepoName;
        this.quizTemplateRepoOwner = quizTemplateRepoOwner;
        this.quizTemplateRepoName = quizTemplateRepoName;
        this.circuitTemplateOwner = circuitTemplateOwner;
        this.circuitTemplateRepo = circuitTemplateRepo;
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
     * Creates a Gitea user account using admin privileges.
     * @param username The username of the user to create.
     * @param email The user's email address.
     * @param password The user's password.
     * @return The Gitea user ID.
     * @throws ExternalServiceException If an error occurs (e.g. user already exists, or API error).
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
     * Deletes a user from Gitea.
     * WARNING: This permanently deletes the user and all repositories they own!
     * @param username The Gitea login name of the user to delete.
     * @throws ExternalServiceException If an error occurs (e.g. user not found).
     */
    public void deleteGiteaUser(String username) {
        log.info("Attempting to delete Gitea user: {}", username);
        try {
            restClient.delete()
                    .uri("/admin/users/{username}", username)
                    .retrieve()
                    .toBodilessEntity(); // Expecting a 204 No Content response
            log.info("Successfully deleted Gitea user: {}", username);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Gitea user '{}' not found, skipping deletion. Error: {}", username, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete Gitea user '{}'. Error: {}", username, e.getMessage());
            throw new ExternalServiceException("Gitea", "Failed to delete user: " + e.getMessage());
        }
    }

    /**
     * Creates a new, empty repository under the administrator user.
     *
     * @param repoName The name of the repository to create (e.g. "mission-1-template").
     * @param isPrivate Whether the repository should be private.
     * @return The clone URL of the new repository (clone_url).
     */
    public String createEmptyRepository(String repoName, boolean isPrivate) {
        log.info("Attempting to create empty Gitea repository '{}' as admin.", repoName);
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", repoName);
        requestBody.put("private", isPrivate);
        requestBody.put("auto_init", false);
        // description, license can also be set

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
     * Deletes a repository from Gitea.
     * @param owner The name of the repository owner.
     * @param repoName The name of the repository to delete.
     * @throws ExternalServiceException If an error occurs (e.g. repo not found, or insufficient permissions).
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
     * Deletes a repository owned by the admin user.
     * @param repoName The name of the repository to delete.
     * @throws ExternalServiceException If an error occurs.
     */
    public void deleteAdminRepository(String repoName) {
        deleteRepository(this.adminUsername, repoName);
    }

    /**
     * Recursively copies the contents (files and folders) of one repository into another.
     * Both repositories must be owned by the admin for this operation.
     *
     * @param sourceOwner    The name of the source repository owner.
     * @param sourceRepoName The name of the source repository.
     * @param targetRepoName The name of the target repository (under admin).
     * @throws ExternalServiceException If an error occurs during the copy.
     */
    public void copyRepositoryContents(String sourceOwner, String sourceRepoName, String targetRepoName) {
        log.info("Collecting contents from {}/{} to copy to admin's {}", sourceOwner, sourceRepoName, targetRepoName);
        Map<String, String> allFiles = new HashMap<>();
        collectFilesRecursive(sourceOwner, sourceRepoName, "", allFiles);

        // A single large commit with all files
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
     * Uploads multiple files in a single commit.
     * @param files A Map where the key is the file path and the value is the content.
     * @param "operation" "create" or "update" (depending on the Gitea API).
     */
    public void uploadFiles(String repoOwner, String repoName, Map<String, String> files, String commitMessage, Cadet user) {
        if (files == null || files.isEmpty()) return;

        // 1. Fetch current files to know their SHAs (if they exist)
        // This is important because the Batch API doesn't support "upsert" (create or update) — we must specify
        Map<String, String> currentShas = new HashMap<>();
        try {
            // Iterate over the root directory (and recursively if needed, but for simplicity focus on top-level files)
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

            // If the file already exists: UPDATE + SHA, otherwise: CREATE
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
        requestBody.put("branch", "main"); // Always commit to main

        // Set author...
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
     * Uploads or updates a file in the specified repository.
     * If the file exists, it updates it; if not, it creates it.
     * @param repoOwner The name of the repository owner.
     * @param repoName The name of the repository.
     * @param filePath The path of the file within the repo.
     * @param content The content to upload as a string.
     * @return The file URL.
     * @throws ExternalServiceException If an error occurs during the operation.
     */
    public String uploadFile(String repoOwner, String repoName, String filePath, String content, Cadet user) {
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
                // Update (PUT)
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
                // Create (POST)
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
            // Direct URI concatenation is also important here
            String uri = String.format("/repos/%s/%s/contents/%s", owner, repoName, filePath);
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (HttpClientErrorException.NotFound e) {
            return null; // This is fine for the if-branch in uploadFile
        } catch (Exception e) {
            log.error("Error getting file info for {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    public void setRepositorySecret(String repoName, String secretName, String secretValue) {
        Map<String, String> body = new HashMap<>();
        body.put("data", secretValue);

        // Gitea API endpoint for setting the secret
        restClient.put()
                .uri("/repos/{owner}/{repo}/actions/secrets/{secret_name}", adminUsername, repoName, secretName)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Retrieves the contents of a directory (file listing).
     * @param owner The username of the repository owner.
     * @param repoName The name of the repository.
     * @param path The path of the directory within the repo.
     * @return The contents of the directory.
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
     * Retrieves the content of a file (as a decoded String).
     * @param owner The username of the repository owner.
     * @param repoName The name of the repository.
     * @param filePath The path of the file.
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
     * Creates a new mission repository from a template and adds the user as a collaborator.
     * The repository remains owned by the admin.
     *
     * @param missionIdString The name of the new repository (recommended to be the Mission UUID string).
     * @param templateLanguage The language of the template (e.g. "javascript", "python").
     * @param user             The Cadet object who will get access to the repo.
     * @return The clone URL of the new repository.
     * @throws ExternalServiceException If an error occurs during Gitea operations.
     */
    public String createMissionRepository(String missionIdString, String templateLanguage, Cadet user, MissionType type) {
        log.info("Creating mission repository for user '{}' from '{}' template.", user.getUsername(), templateLanguage);

        String sourceOwner;
        String sourceRepoName;
        String newRepoName = missionIdString;

        if (type == MissionType.QUIZ) {
            sourceOwner = quizTemplateRepoOwner;
            sourceRepoName = quizTemplateRepoName;
        } else if (type == MissionType.CIRCUIT_SIMULATION) {
            sourceOwner = circuitTemplateOwner;
            sourceRepoName = circuitTemplateRepo;
        } else if ("javascript".equalsIgnoreCase(templateLanguage)) {
            sourceOwner = jsTemplateRepoOwner;
            sourceRepoName = jsTemplateRepoName;
        } else if ("python".equalsIgnoreCase(templateLanguage)) {
            sourceOwner = pythonTemplateRepoOwner;
            sourceRepoName = pythonTemplateRepoName;
        } else {
            throw new IllegalArgumentException("Unsupported template language: " + templateLanguage);
        }

        // 1. Create empty repo under admin
        String newRepoCloneUrl = createEmptyRepository(newRepoName, true);

        setRepositorySecret(newRepoName, "MISSION_VERIFICATION_SECRET", this.verificationSecretValue);

        // 2. Copy template contents into the new repo
        copyRepositoryContents(sourceOwner, sourceRepoName, newRepoName);

        // 3. Add user as collaborator with 'write' access
        addCollaborator(newRepoName, user.getUsername(), "write");
        log.info("Mission repository '{}' created and user '{}' added as collaborator.", newRepoName, user.getUsername());

        return newRepoCloneUrl;
    }

    /**
     * Returns the SHA of the latest commit on the default branch of an admin-owned repo.
     * Used as part of the compile cache key.
     *
     * @param repoName The repository name (under admin account).
     * @return The latest commit SHA, or null if the repo is empty / not found.
     */
    public String getLatestCommitHash(String repoName) {
        try {
            List<Map<String, Object>> commits = restClient.get()
                    .uri("/repos/{owner}/{repo}/commits?limit=1", adminUsername, repoName)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (commits != null && !commits.isEmpty()) {
                return (String) commits.get(0).get("sha");
            }
        } catch (Exception e) {
            log.warn("Could not fetch latest commit hash for repo '{}': {}", repoName, e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves a repository by name under a given owner.
     * @param owner The name of the repository owner.
     * @param repoName The name of the repository to query.
     * @return Optional<Map<String, Object>> - The repository data, if it exists.
     * @throws ExternalServiceException If an API error occurs (except 404).
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
            return Optional.empty(); // Repository not found
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
        private String type; // "file" or "dir"
        private String content; // Base64
        private String download_url;
    }

    /**
     * Validates that the file path does not contain a path traversal attempt.
     * Only allows alphanumeric characters, dots, hyphens, underscores, and slashes.
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
     * Adds a user (collaborator) to a repo.
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