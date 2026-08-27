package com.legymernok.backend.service.rag.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PythonMethodSplitterTest {

    private final PythonMethodSplitter splitter = new PythonMethodSplitter();

    @Test
    @DisplayName("egyetlen def -> egy chunk, a teljes függvénytörzzsel")
    void split_singleFunction_returnsOneChunk() {
        String source = """
                import math


                def area(r):
                    return math.pi * r * r
                """;

        List<String> chunks = splitter.split(source);

        assertThat(chunks).hasSize(2); // az import-fejléc + a függvény
        assertThat(chunks.get(1)).contains("def area(r):").contains("return math.pi");
    }

    @Test
    @DisplayName("három top-level def -> három külön chunk")
    void split_multipleTopLevelFunctions_returnsSeparateChunks() {
        String source = """
                def first():
                    return 1

                def second():
                    return 2

                def third():
                    return 3
                """;

        List<String> chunks = splitter.split(source);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).contains("def first():").doesNotContain("def second");
        assertThat(chunks.get(1)).contains("def second():");
        assertThat(chunks.get(2)).contains("def third():");
    }

    @Test
    @DisplayName("beágyazott helper def a szülő chunkjában marad")
    void split_nestedHelperFunction_staysInParentChunk() {
        String source = """
                def outer(values):
                    def helper(x):
                        return x * 2
                    return [helper(v) for v in values]

                def other():
                    return None
                """;

        List<String> chunks = splitter.split(source);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).contains("def outer").contains("def helper");
    }

    @Test
    @DisplayName("osztály-metódusok külön chunkok, a fájl-fejléc (class-deklaráció) önálló chunk")
    void split_classMethods_areSeparateChunks() {
        String source = """
                class Solver:
                    def solve(self):
                        return 42

                    def reset(self):
                        pass
                """;

        List<String> chunks = splitter.split(source);

        // Az első "def" ELŐTTI rész (import, class-deklaráció, dekorátor) önálló chunk —
        // így az importok nem vesznek el. A metódusok viszont külön chunkok.
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).contains("class Solver:");
        assertThat(chunks.get(1)).contains("def solve").doesNotContain("def reset");
        assertThat(chunks.get(2)).contains("def reset");
    }

    @Test
    @DisplayName("def nélküli fájl -> üres lista (fallback jelzés a hívónak)")
    void split_noFunctions_returnsEmptyList() {
        String source = """
                import os

                MAX_RETRIES = 3
                TIMEOUT = 30
                """;

        assertThat(splitter.split(source)).isEmpty();
        assertThat(splitter.split(null)).isEmpty();
        assertThat(splitter.split("   ")).isEmpty();
    }
}
