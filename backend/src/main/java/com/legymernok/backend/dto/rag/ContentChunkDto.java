package com.legymernok.backend.dto.rag;

import java.util.UUID;

/**
 * Egy RAG chunk, ahogy egy keresési válaszban megjelenik.
 *
 * @param filePath üres string a {@code MISSION} / {@code MISSION_FILL_IN_BLANK} típusoknál,
 *                 a tényleges fájl-útvonal {@code MISSION_CODE_FILE}-nál
 * @param score    csak retrieval-válaszban töltött (RRF/rerank pontszám), indexeléskor 0.0
 */
public record ContentChunkDto(
        UUID id,
        String sourceType,
        UUID sourceId,
        String filePath,
        int chunkIndex,
        String chunkText,
        double score
) {}
