package com.legymernok.backend.service.rag.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeFileChunkerTest {

    @Mock private PythonMethodSplitter pythonSplitter;
    @Mock private JsMethodSplitter jsSplitter;
    @Mock private TextChunker textChunker;

    @InjectMocks private CodeFileChunker chunker;

    @Test
    @DisplayName("csak a forráskód-kiterjesztések indexelhetők")
    void isIndexableSourceFile_whitelist() {
        assertThat(chunker.isIndexableSourceFile("solution.py")).isTrue();
        assertThat(chunker.isIndexableSourceFile("src/Solution.TS")).isTrue();
        assertThat(chunker.isIndexableSourceFile("app.jsx")).isTrue();

        assertThat(chunker.isIndexableSourceFile("README.md")).isFalse();
        assertThat(chunker.isIndexableSourceFile("quiz.json")).isFalse();
        assertThat(chunker.isIndexableSourceFile(".gitea/workflows/ci.yml")).isFalse();
        assertThat(chunker.isIndexableSourceFile("Makefile")).isFalse();
        assertThat(chunker.isIndexableSourceFile(null)).isFalse();
    }

    @Test
    @DisplayName(".py fájl a Python-splitterre megy, a JS-splitter nem hívódik")
    void chunkFile_pythonFile_usesPythonSplitter() {
        when(pythonSplitter.split("body")).thenReturn(List.of("def a(): pass"));

        assertThat(chunker.chunkFile("solution.py", "body")).containsExactly("def a(): pass");
        verify(jsSplitter, never()).split(any());
        verify(textChunker, never()).chunk(any());
    }

    @Test
    @DisplayName("nem indexelhető kiterjesztésnél a chunkFile üres eredményre esik vissza")
    void chunkFile_unindexableExtension_fallsBackToTextChunker() {
        when(textChunker.chunk("{}")).thenReturn(List.of("{}"));

        assertThat(chunker.chunkFile("quiz.json", "{}")).containsExactly("{}");
        verify(pythonSplitter, never()).split(any());
        verify(jsSplitter, never()).split(any());
    }

    @Test
    @DisplayName("ha a splitter nem talál függvényt, a bekezdés-alapú vágás fut le helyette")
    void chunkFile_noFunctionsFound_fallsBackToChunkText() {
        when(pythonSplitter.split("MAX = 3")).thenReturn(List.of());
        when(textChunker.chunk("MAX = 3")).thenReturn(List.of("MAX = 3"));

        assertThat(chunker.chunkFile("consts.py", "MAX = 3")).containsExactly("MAX = 3");
    }
}
