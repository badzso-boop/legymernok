package com.legymernok.backend.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

@Service
public class GiteaService {

    private final RestClient restClient;

    public GiteaService(
            @Value("${gitea.api.url}") String apiUrl,
            @Value("${gitea.admin.username}") String adminUsername,
            @Value("${gitea.admin.password}") String adminPassword) {

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
}