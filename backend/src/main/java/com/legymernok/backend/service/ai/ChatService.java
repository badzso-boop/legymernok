package com.legymernok.backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.dto.chat.ChatAction;
import com.legymernok.backend.dto.chat.ChatContextDto;
import com.legymernok.backend.dto.chat.ChatRequest;
import com.legymernok.backend.dto.chat.ChatResponse;
import com.legymernok.backend.dto.search.StarSystemSearchResult;
import com.legymernok.backend.service.starsystem.StarSystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final AiEmbeddingService embeddingService;
    private final StarSystemService starSystemService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.service.url:http://localhost:8081}")
    private String aiServiceUrl;

    private static final String SYSTEM_PROMPT = """
            Te egy LégyMérnök.hu platform asszisztens vagy. A LégyMérnök.hu egy gamifikált oktatási platform mérnökhallgatóknak.

            SZABÁLYOK:
            1. KIZÁRÓLAG a LégyMérnök.hu platformmal kapcsolatos kérdésekre válaszolhatsz.
            2. Ha más témáról kérdeznek, udvariasan jelezd: "Csak a LégyMérnök platformmal kapcsolatos kérdésekben tudok segíteni."
            3. Ha a felhasználó form kitöltést kér (pl. "töltsd ki", "segíts létrehozni", "írd be"), ÉS a jelenlegi oldal egy szerkesztő oldal (STAR_SYSTEM_CREATE, STAR_SYSTEM_EDIT, MISSION_CREATE, MISSION_EDIT), a válaszod VÉGÉN adj meg PONTOSAN egy JSON blokkot így:
               {"action":"FILL_FORM","fields":{"name":"...","description":"..."}}
            4. Csak azokat a mezőket szerepeltesd a JSON-ban, amiket a felhasználó kért.
            5. Rövid, tömör válaszokat adj. Maximum 3-4 bekezdés.

            A PLATFORMRÓL:
            - Csillagrendszerek (star systems): tananyag-gyűjtemények
            - Missziók típusai: CODING, QUIZ, CONTENT, FILL_IN_BLANK
            - Kadétok teljesítik a missziókat
            - Nehézségek: EASY, MEDIUM, HARD, EXPERT
            """;

    private static final Map<String, String> PAGE_TYPE_HINTS = Map.of(
            "STAR_SYSTEM_CREATE", "A felhasználó ÚJ csillagrendszert hoz létre. Kitölthető mezők: name (név), description (leírás), iconUrl.",
            "STAR_SYSTEM_EDIT", "A felhasználó MEGLÉVŐ csillagrendszert szerkeszt. Kitölthető mezők: name (név), description (leírás), iconUrl.",
            "MISSION_CREATE", "A felhasználó ÚJ missziót hoz létre. Kitölthető mezők: name (név), descriptionMarkdown (leírás), difficulty (EASY/MEDIUM/HARD/EXPERT), missionType (CODING/QUIZ/CONTENT/FILL_IN_BLANK).",
            "MISSION_EDIT", "A felhasználó MEGLÉVŐ missziót szerkeszt. Kitölthető mezők: name (név), descriptionMarkdown (leírás), difficulty, missionType.",
            "STAR_MAP", "A felhasználó a csillagtérképet nézi. Kérdezhet csillagrendszerekről.",
            "STAR_SYSTEM_DETAIL", "A felhasználó egy csillagrendszer misszióit böngészi.",
            "MISSION_FORGE", "A felhasználó missziót szerkeszt a Mission Forge-ban.",
            "GENERAL", ""
    );

    private static final Pattern FILL_FORM_PATTERN = Pattern.compile(
            "\\{\\s*\"action\"\\s*:\\s*\"FILL_FORM\".*?\\}",
            Pattern.DOTALL
    );

    public ChatResponse chat(ChatRequest request, String username) {
        // 1. Szemantikus keresés
        List<StarSystemSearchResult> relevant = List.of();
        try {
            AiEmbeddingService.Embedding vector = embeddingService.embedQuery(request.message());
            if (vector != null) {
                relevant = starSystemService.searchByEmbedding(
                        embeddingService.toVectorString(vector.vector()), 3);
            }
        } catch (Exception e) {
            log.warn("Semantic search failed during chat: {}", e.getMessage());
        }

        // 2. Kontextus összeállítása
        List<String> contextLines = buildContextLines(request.context(), username, relevant);

        // 3. Generate hívás
        String rawResponse = callGenerate(request.message(), contextLines);
        if (rawResponse == null) {
            return new ChatResponse("Sajnálom, az AI szolgáltatás jelenleg nem érhető el.", null);
        }

        // 4. FILL_FORM parse
        ChatAction action = parseAction(rawResponse);
        String cleanResponse = removeActionJson(rawResponse).trim();

        return new ChatResponse(cleanResponse, action);
    }

    private List<String> buildContextLines(ChatContextDto ctx, String username, List<StarSystemSearchResult> relevant) {
        List<String> lines = new ArrayList<>();
        lines.add("Felhasználó: " + username);

        if (ctx != null) {
            lines.add("Jelenlegi oldal: " + ctx.currentPage() + " (típus: " + ctx.pageType() + ")");

            String hint = PAGE_TYPE_HINTS.getOrDefault(ctx.pageType(), "");
            if (!hint.isEmpty()) lines.add(hint);

            if (ctx.formFields() != null && !ctx.formFields().isEmpty()) {
                StringBuilder formSb = new StringBuilder("Jelenleg kitöltött mezők:");
                ctx.formFields().forEach((k, v) -> {
                    if (v != null && !v.isBlank()) formSb.append("\n  - ").append(k).append(": ").append(v);
                });
                lines.add(formSb.toString());
            }

            if ("hu".equals(ctx.language())) {
                lines.add("A felhasználó nyelve: magyar. Válaszolj magyarul.");
            }
        }

        if (!relevant.isEmpty()) {
            StringBuilder sb = new StringBuilder("Releváns csillagrendszerek a platformon:");
            for (StarSystemSearchResult r : relevant) {
                sb.append("\n  - \"").append(r.getName()).append("\"");
                if (r.getDescription() != null && !r.getDescription().isBlank()) {
                    sb.append(": ").append(r.getDescription(), 0, Math.min(120, r.getDescription().length()));
                }
                sb.append(" (egyezés: ").append(String.format("%.0f%%", r.getSimilarity() * 100)).append(")");
            }
            lines.add(sb.toString());
        }

        return lines;
    }

    @SuppressWarnings("unchecked")
    private String callGenerate(String message, List<String> contextLines) {
        try {
            var body = Map.of(
                    "prompt", message,
                    "context", contextLines,
                    "system_prompt", SYSTEM_PROMPT
            );
            var req = RequestEntity
                    .post(aiServiceUrl + "/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
            var response = restTemplate.exchange(req, Map.class);
            if (response.getBody() == null) return null;
            return (String) response.getBody().get("response");
        } catch (Exception e) {
            log.error("Generate call failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private ChatAction parseAction(String text) {
        if (text == null) return null;
        Matcher m = FILL_FORM_PATTERN.matcher(text);
        if (!m.find()) return null;
        try {
            Map<String, Object> parsed = objectMapper.readValue(m.group(), Map.class);
            if (!"FILL_FORM".equals(parsed.get("action"))) return null;
            Object fieldsObj = parsed.get("fields");
            if (!(fieldsObj instanceof Map)) return null;
            Map<String, String> fields = (Map<String, String>) fieldsObj;
            return new ChatAction("FILL_FORM", fields);
        } catch (Exception e) {
            log.warn("Could not parse FILL_FORM action from response: {}", e.getMessage());
            return null;
        }
    }

    private String removeActionJson(String text) {
        if (text == null) return "";
        return FILL_FORM_PATTERN.matcher(text).replaceAll("").trim();
    }
}
