package com.legymernok.backend.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiEmbeddingService {

    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://localhost:8081}")
    private String aiServiceUrl;

    public float[] embed(String text) {
        try {
            var request = RequestEntity
                    .post(aiServiceUrl + "/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text));

            var response = restTemplate.exchange(request, EmbedResponse.class);
            if (response.getBody() == null) return null;
            List<Float> list = response.getBody().embedding();
            float[] arr = new float[list.size()];
            for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
            return arr;
        } catch (Exception e) {
            log.warn("Embedding failed for text '{}': {}", text.substring(0, Math.min(50, text.length())), e.getMessage());
            return null;
        }
    }

    public String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    record EmbedResponse(List<Float> embedding, String model) {}
}
