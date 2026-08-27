package com.legymernok.backend.web.search;

import com.legymernok.backend.dto.search.StarSystemSearchResult;
import com.legymernok.backend.service.ai.AiEmbeddingService;
import com.legymernok.backend.service.rag.ContentChunkingService;
import com.legymernok.backend.service.starsystem.StarSystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SearchController {

    private final AiEmbeddingService embeddingService;
    private final StarSystemService starSystemService;
    private final ContentChunkingService contentChunkingService;

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('starsystem:read')")
    public ResponseEntity<List<StarSystemSearchResult>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int limit) {

        AiEmbeddingService.Embedding vector = embeddingService.embedQuery(q);
        if (vector == null) {
            return ResponseEntity.ok(List.of());
        }
        String vectorStr = embeddingService.toVectorString(vector.vector());
        return ResponseEntity.ok(starSystemService.searchByEmbedding(vectorStr, limit));
    }

    @PostMapping("/admin/reindex-star-systems")
    @PreAuthorize("hasAuthority('starsystem:edit_any')")
    public ResponseEntity<ReindexResponse> reindex() {
        int count = starSystemService.reindexAllStarSystems();
        return ResponseEntity.ok(new ReindexResponse(count));
    }

    /**
     * A teljes RAG chunk-index újraépítése. Ugyanaz a permission, mint a
     * csillagrendszer-reindexnél: admin-szintű, tartalom-karbantartó művelet.
     */
    @PostMapping("/admin/reindex-content")
    @PreAuthorize("hasAuthority('starsystem:edit_any')")
    public ResponseEntity<ReindexContentResponse> reindexContent() {
        int count = contentChunkingService.reindexAllMissions();
        return ResponseEntity.ok(new ReindexContentResponse(count));
    }

    record ReindexResponse(int indexed) {}

    record ReindexContentResponse(int missionsIndexed) {}
}
