package com.legymernok.backend.service.rag.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CODING-misszió kódfájljainak chunkolása: fájl-szűrés, nyelv-detektálás, diszpécselés.
 *
 * <p>A kiterjesztés-whitelist tudatosan szűk: minden más fájl (README.md, package.json,
 * requirements.txt, CI-workflow) kimarad. A misszió leírása amúgy is indexelve van a
 * {@code reindexMission()}-ön keresztül, tehát nem veszik el, csak nem duplikálódik.
 */
@Component
@RequiredArgsConstructor
public class CodeFileChunker {

    private static final Set<String> INDEXABLE_EXTENSIONS = Set.of("py", "js", "jsx", "ts", "tsx");

    private final PythonMethodSplitter pythonSplitter;
    private final JsMethodSplitter jsSplitter;
    private final TextChunker textChunker;

    public boolean isIndexableSourceFile(String filePath) {
        return INDEXABLE_EXTENSIONS.contains(extensionOf(filePath));
    }

    /**
     * Egy fájl chunkjai — lehetőleg metódusonként.
     *
     * <p>Ha a splitter nem talál egyetlen függvényt sem (pl. csak konstansokat tartalmazó
     * fájl, vagy nem parse-olható szintaxis), visszaesünk a bekezdés-alapú vágásra: így
     * egyetlen indexelhető fájl sem marad ki, legfeljebb nem metódus-pontosan van vágva.
     */
    public List<String> chunkFile(String filePath, String content) {
        List<String> chunks = switch (extensionOf(filePath)) {
            case "py" -> pythonSplitter.split(content);
            case "js", "jsx", "ts", "tsx" -> jsSplitter.split(content);
            default -> List.of();
        };
        return chunks.isEmpty() ? textChunker.chunk(content) : chunks;
    }

    private String extensionOf(String filePath) {
        if (filePath == null) return "";
        int dot = filePath.lastIndexOf('.');
        if (dot < 0 || dot == filePath.length() - 1) return "";
        return filePath.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
