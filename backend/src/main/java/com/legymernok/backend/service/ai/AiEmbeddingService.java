package com.legymernok.backend.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Beágyazás-generálás az ai-service {@code /embed} végpontján keresztül.
 *
 * <p><b>Miért két metódus egy helyett:</b> a jelenlegi {@code EMBED_MODEL}
 * (nomic-embed-text v1.5) <i>task-prefixekkel</i> van tanítva — a dokumentumokat
 * {@code search_document: }, a lekérdezéseket {@code search_query: } prefixszel várja.
 * Prefix nélkül a kérdés- és a dokumentum-vektorok nem ugyanabba az altérbe esnek, és a
 * koszinusz-hasonlóság mérhetően romlik. Ez <b>csendes</b> minőségromlás: semmilyen hibát
 * nem dob, csak rosszabb találatokat ad. Egyetlen, kétértelmű {@code embed(String)} metódus
 * mellett a hívási helyen nem derül ki, melyik oldalról van szó — ezért nincs ilyen metódus.
 *
 * <p>A prefixek konfigurálhatók, mert modell-specifikusak: egy nem-nomic modellre váltva a
 * prefix nem javít, hanem ront. Üres értékre állítva a viselkedés a prefix nélküli.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiEmbeddingService {

    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://localhost:8081}")
    private String aiServiceUrl;

    @Value("${ai.embed.document-prefix:search_document: }")
    private String documentPrefix;

    @Value("${ai.embed.query-prefix:search_query: }")
    private String queryPrefix;

    /**
     * Indexelendő tartalom beágyazása.
     *
     * @return a vektor és az őt előállító modell neve, vagy {@code null}, ha az ai-service
     *         nem elérhető / hibázott (a hívók graceful degradationnel kezelik).
     */
    public Embedding embedDocument(String text) {
        return call(documentPrefix + text, text);
    }

    /**
     * Keresési kérdés beágyazása.
     *
     * @return a vektor és az őt előállító modell neve, vagy {@code null} hiba esetén.
     */
    public Embedding embedQuery(String text) {
        return call(queryPrefix + text, text);
    }

    private Embedding call(String prefixedText, String originalText) {
        try {
            var request = RequestEntity
                    .post(aiServiceUrl + "/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", prefixedText));

            var response = restTemplate.exchange(request, EmbedResponse.class);
            EmbedResponse body = response.getBody();
            if (body == null || body.embedding() == null) return null;

            List<Float> list = body.embedding();
            float[] arr = new float[list.size()];
            for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
            return new Embedding(arr, body.model());
        } catch (Exception e) {
            log.warn("Embedding failed for text '{}': {}",
                    originalText.substring(0, Math.min(50, originalText.length())), e.getMessage());
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

    /**
     * Egy beágyazás és az őt előállító modell neve.
     *
     * <p>A modellnév azért utazik a vektorral együtt, hogy indexeléskor el lehessen tárolni
     * ({@code content_chunks.embedding_model}) — enélkül egy modellváltás csendben
     * inkonzisztens indexet hagyna, és semmi nem jelezné, hogy a reindex megtörtént-e.
     */
    public record Embedding(float[] vector, String model) {}

    record EmbedResponse(List<Float> embedding, String model) {}
}
