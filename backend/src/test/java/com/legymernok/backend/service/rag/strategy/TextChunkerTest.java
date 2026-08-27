package com.legymernok.backend.service.rag.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@link TextChunker} pure function — mock nélkül, közvetlen bemenet→kimenet assertekkel
 * tesztelhető.
 */
class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    private static String repeat(String seed, int length) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length) sb.append(seed);
        return sb.substring(0, length);
    }

    @Test
    @DisplayName("800 karakternél rövidebb szöveg egyetlen, változatlan chunk marad")
    void chunkText_shortText_returnsSingleChunk() {
        String text = "Rövid misszió-leírás.\n\nMásodik bekezdés.";

        assertThat(chunker.chunk(text)).containsExactly(text);
    }

    @Test
    @DisplayName("null / üres / whitespace bemenet üres listát ad")
    void chunkText_null_returnsEmptyList() {
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("")).isEmpty();
        assertThat(chunker.chunk("   \n\n  \t ")).isEmpty();
    }

    @Test
    @DisplayName("két egymást követő chunk átfedése pontosan 150 karakter")
    void chunkText_longText_respectsOverlap() {
        // 12 bekezdés × ~200 karakter — biztosan több chunkra esik szét.
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            text.append(repeat("bekezdes" + i + " ", 200)).append("\n\n");
        }

        List<String> chunks = chunker.chunk(text.toString());

        assertThat(chunks).hasSizeGreaterThan(1);
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1);
            String expectedOverlap = previous.substring(previous.length() - TextChunker.OVERLAP);
            assertThat(chunks.get(i))
                    .as("a(z) %d. chunk az előző utolsó 150 karakterével kezdődik", i)
                    .startsWith(expectedOverlap);
        }
    }

    @Test
    @DisplayName("egyetlen chunk sem lépi túl a kemény felső korlátot")
    void chunkText_neverExceedsMaxChunk() {
        StringBuilder text = new StringBuilder();
        // 700 karakteres bekezdések: a "hozzáadás után ellenőrzünk" szabály önmagában
        // 1400-as chunkot hozna létre — a MAX_CHUNK korlátnak ezt meg kell akadályoznia.
        for (int i = 0; i < 8; i++) {
            text.append(repeat("x", 700)).append("\n\n");
        }

        assertThat(chunker.chunk(text.toString()))
                .allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(TextChunker.MAX_CHUNK));
    }

    @Test
    @DisplayName("bekezdés-határon vág, nem szó közepén")
    void chunkText_prefersParagraphBoundary() {
        String paragraph = repeat("alma ", 500);
        String text = paragraph + "\n\n" + paragraph + "\n\n" + paragraph;

        List<String> chunks = chunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        // Az első chunk pontosan az első bekezdésnél zárul (a következő bekezdés már
        // átlépné a korlátot), tehát a végén nincs félbevágott szó.
        assertThat(chunks.get(0)).endsWith(paragraph);
    }

    @Test
    @DisplayName("egyetlen, MAX_CHUNK-nál hosszabb bekezdést kemény vágással darabol")
    void chunkText_singleHugeParagraph_hardCuts() {
        String huge = repeat("abcdefghij", 2000); // egyetlen bekezdés, nincs \n\n

        List<String> chunks = chunker.chunk(huge);

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allSatisfy(
                chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(TextChunker.TARGET_CHUNK));
        // A darabok hézagmentesen lefedik az eredetit (átfedéssel, de kihagyás nélkül).
        assertThat(chunks.get(0)).isEqualTo(huge.substring(0, TextChunker.TARGET_CHUNK));
        assertThat(huge).endsWith(chunks.get(chunks.size() - 1));
    }
}
