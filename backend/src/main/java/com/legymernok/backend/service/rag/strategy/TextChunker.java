package com.legymernok.backend.service.rag.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Bekezdés-alapú szövegvágás átfedéssel — a RAG-index alap-stratégiája sima szövegre
 * (misszió-leírás, fill-in-blank sablon) és a kód-splitterek fallbackje.
 *
 * <p>Pure function: nincs benne DB, HTTP, semmilyen side-effect — közvetlen
 * bemenet→kimenet assertekkel tesztelhető.
 *
 * <p><b>A méret-szabály</b>: a {@code \n\n} bekezdéshatár az elsődleges, de van egy kemény
 * felső korlát ({@link #MAX_CHUNK}). Enélkül a "hozzáadás UTÁN ellenőrizzük a hosszt"
 * szabály tetszőlegesen nagy chunkot engedne (799 karakteres puffer + 700 karakteres
 * bekezdés = 1499). Ha egy bekezdés hozzáadása átlépné a korlátot, a bekezdés nem kerül
 * hozzá az aktuális chunkhoz, hanem újat kezd. Így a chunkok a {@code [~150, 1200]}
 * tartományban maradnak, és az átfedés mindig pontosan {@link #OVERLAP} karakter.
 */
@Component
public class TextChunker {

    /** Ekkora hossz elérésekor lezárjuk az aktuális chunkot. */
    static final int TARGET_CHUNK = 800;
    /** Ennyi karakterrel lóg át egy chunk a következőbe (kontextus-folytonosság). */
    static final int OVERLAP = 150;
    /** Kemény felső korlát — efölé egy chunk sose nőhet. */
    static final int MAX_CHUNK = 1200;

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) return List.of();

        String normalized = text.replace("\r\n", "\n");
        if (normalized.length() <= TARGET_CHUNK) return List.of(normalized);

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        // Hány karakternyi átfedést hoztunk át az előző chunkból: ennél többet tartalmazó
        // puffer az, ami valódi, még le nem zárt tartalmat hordoz.
        int carried = 0;

        for (String paragraph : normalized.split("\n\n")) {
            if (paragraph.isBlank()) continue;

            if (paragraph.length() > MAX_CHUNK) {
                if (current.length() > carried) chunks.add(current.toString());
                hardCut(paragraph, chunks);
                current = new StringBuilder(overlapOf(chunks.get(chunks.size() - 1)));
                carried = current.length();
                continue;
            }

            int separator = current.length() > 0 ? 2 : 0;
            if (current.length() > carried
                    && current.length() + separator + paragraph.length() > MAX_CHUNK) {
                chunks.add(current.toString());
                current = new StringBuilder(overlapOf(current.toString()));
                carried = current.length();
            }

            if (current.length() > 0) current.append("\n\n");
            current.append(paragraph);

            if (current.length() >= TARGET_CHUNK) {
                chunks.add(current.toString());
                current = new StringBuilder(overlapOf(current.toString()));
                carried = current.length();
            }
        }

        // A maradék csak akkor önálló chunk, ha többet tartalmaz az előző chunk átfedésénél
        // — különben szó szerinti duplikátum lenne.
        if (current.length() > carried) chunks.add(current.toString());
        return chunks;
    }

    /** Egyetlen, {@link #MAX_CHUNK}-nál hosszabb bekezdés darabolása fix ablakkal. */
    private void hardCut(String paragraph, List<String> chunks) {
        int pos = 0;
        while (pos < paragraph.length()) {
            int end = Math.min(pos + TARGET_CHUNK, paragraph.length());
            chunks.add(paragraph.substring(pos, end));
            if (end == paragraph.length()) return;
            pos = end - OVERLAP;
        }
    }

    private String overlapOf(String chunk) {
        return chunk.substring(Math.max(0, chunk.length() - OVERLAP));
    }
}
