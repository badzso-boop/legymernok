package com.legymernok.backend.service.rag.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Python forrásfájl vágása függvény-határokon, indentáció alapján.
 *
 * <p>Egy chunk = egy <b>top-level indentációs szintű</b> {@code def} (modul-szintű függvény
 * vagy osztály-metódus). A beágyazott helper-függvényeket <b>szándékosan</b> a szülőjük
 * chunkjában hagyjuk: egy helper önmagában, a hívási kontextusa nélkül ritkán értelmes
 * RAG-találat, a szülő törzse pedig e nélkül hiányos lenne.
 *
 * <p>Ha a fájlban egyetlen {@code def} sincs (pl. csak konstansok), üres listát ad — ez a
 * hívó {@link CodeFileChunker}-nek a jelzés, hogy essen vissza a bekezdés-alapú
 * {@code chunkText()}-re.
 */
@Component
public class PythonMethodSplitter {

    public List<String> split(String content) {
        if (content == null || content.isBlank()) return List.of();

        String[] lines = content.split("\n", -1);

        Integer topLevelIndent = findTopLevelDefIndent(lines);
        if (topLevelIndent == null) return List.of();

        List<String> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String line : lines) {
            if (isDefLine(line) && indentOf(line) == topLevelIndent) {
                appendIfNotBlank(chunks, buffer);
                buffer = new StringBuilder();
            }
            if (buffer.length() > 0) buffer.append("\n");
            buffer.append(line);
        }
        appendIfNotBlank(chunks, buffer);

        return chunks;
    }

    /**
     * A fájlban előforduló legkisebb {@code def} indentációs szint. Ez azért nem fixen 0,
     * mert egy osztály-metódus szintje 4 (a {@code class} alatt) — ott az a "top level".
     */
    private Integer findTopLevelDefIndent(String[] lines) {
        Integer min = null;
        for (String line : lines) {
            if (!isDefLine(line)) continue;
            int indent = indentOf(line);
            if (min == null || indent < min) min = indent;
        }
        return min;
    }

    private boolean isDefLine(String line) {
        String trimmed = line.stripLeading();
        return trimmed.startsWith("def ") || trimmed.startsWith("async def ");
    }

    private int indentOf(String line) {
        return line.length() - line.stripLeading().length();
    }

    private void appendIfNotBlank(List<String> chunks, StringBuilder buffer) {
        String text = buffer.toString();
        if (!text.isBlank()) chunks.add(text);
    }
}
