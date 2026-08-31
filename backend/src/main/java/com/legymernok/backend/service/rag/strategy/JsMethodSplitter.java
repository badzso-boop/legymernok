package com.legymernok.backend.service.rag.strategy;

import lombok.extern.slf4j.Slf4j;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.FunctionNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * JavaScript/TypeScript forrásfájl vágása függvény-határokon, valódi AST alapján (Rhino).
 *
 * <p><b>Miért parser és nem regex:</b> egy zárójel-számláló heurisztika string-literálban vagy
 * kommentben lévő <code>{</code>/<code>}</code> karakteren elcsúszik — és az nem egzotikus
 * eset, hanem hétköznapi kód (JSDoc, stringes teszt-elvárás). Az AST string- és
 * komment-tudatos, ezt a hibaosztályt szerkezetileg kizárja.
 *
 * <p><b>Ismert korlát:</b> a Rhino nem érti az ES modul {@code import}/{@code export}
 * szintaxist — ezeket parse ELŐTT eltávolítjuk (ez fix mintájú, egyszerű regex-feladat,
 * nem kell benne zárójel-mélységet számolni). Ha a parse ezek után is elhasal (pl. valamilyen
 * nem támogatott ES2022+ elem, vagy TS-típusannotáció), üres listát adunk vissza, és a hívó
 * {@link CodeFileChunker} a bekezdés-alapú vágásra esik vissza — nem törik el semmi, csak
 * kevésbé pontos a vágás.
 *
 * <p>A chunk-szöveg az <b>előfeldolgozott</b> (import/export nélküli) forrásból származik: a
 * hiányzó {@code export} kulcsszó a RAG-kontextusban semmit nem ront, cserébe nem kell
 * pozíció-eltolási térképet karbantartani.
 */
@Component
@Slf4j
public class JsMethodSplitter {

    /**
     * {@code import ... ;} — a {@code [^;]} osztály a sorvégeket is átfogja, tehát a
     * több-soros destructuring import is illeszkedik, de a minta SOSE lép át egy másik
     * utasításba (az első pontosvesszőnél megáll).
     */
    private static final Pattern IMPORT_STATEMENT =
            Pattern.compile("^[ \\t]*import\\s[^;]*;", Pattern.MULTILINE);

    /** Sor eleji {@code export } / {@code export default } prefix. */
    private static final Pattern EXPORT_PREFIX =
            Pattern.compile("^[ \\t]*export\\s+(default\\s+)?", Pattern.MULTILINE);

    public List<String> split(String content) {
        if (content == null || content.isBlank()) return List.of();

        String stripped = stripModuleSyntax(content);

        CompilerEnvirons env = new CompilerEnvirons();
        env.setRecordingComments(true);
        env.setLanguageVersion(Context.VERSION_ES6);
        env.setRecoverFromErrors(false);

        AstRoot root;
        try {
            root = new Parser(env).parse(stripped, "<chunker>", 1);
        } catch (RuntimeException e) {
            // Nem támogatott szintaxis — a hívó chunkText() fallbackre esik.
            log.debug("JS parse failed, falling back to text chunking: {}", e.getMessage());
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        root.visitAll(node -> {
            if (node instanceof FunctionNode fn) {
                int start = fn.getAbsolutePosition();
                int end = Math.min(start + fn.getLength(), stripped.length());
                if (start >= 0 && end > start) {
                    chunks.add(stripped.substring(start, end));
                }
            }
            return true;
        });

        return chunks;
    }

    private String stripModuleSyntax(String content) {
        String withoutImports = IMPORT_STATEMENT.matcher(content).replaceAll("");
        return EXPORT_PREFIX.matcher(withoutImports).replaceAll("");
    }
}
