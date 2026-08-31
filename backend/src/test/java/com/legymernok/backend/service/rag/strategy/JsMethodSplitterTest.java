package com.legymernok.backend.service.rag.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A minta-forrásokat szándékosan a teszt tartalmazza (nem a {@code gitea-templates/}
 * fájlokat olvassuk be): a splitternek a szintaxis-osztályokat kell kezelnie, nem egy
 * konkrét, bármikor átírható sablonfájl aktuális tartalmát.
 */
class JsMethodSplitterTest {

    private final JsMethodSplitter splitter = new JsMethodSplitter();

    /** A {@code mission-js-template/solution.js} tartalma. */
    private static final String SOLUTION_JS = """
            /**
             * Összead két számot.
             * @param {number} a Az első szám.
             * @param {number} b A második szám.
             * @returns {number} A két szám összege.
             */
            export function add(a, b) {
              // Itt van a megoldás
              return a + b;
            }
            """;

    /** A {@code mission-js-template/solution.test.js} tartalma. */
    private static final String SOLUTION_TEST_JS = """
            import { add } from "./solution"; // Modern ES Module import

            describe("add function", () => {
              test("should correctly add two positive numbers", () => {
                expect(add(2, 3)).toBe(5);
              });

              test("should correctly add two negative numbers", () => {
                expect(add(-5, -2)).toBe(-7);
              });
            });
            """;

    @Test
    @DisplayName("export function -> egy chunk, a teljes törzzsel")
    void split_realSolutionJs_findsAddFunction() {
        List<String> chunks = splitter.split(SOLUTION_JS);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0))
                .startsWith("function add(a, b)")
                .contains("return a + b;");
    }

    @Test
    @DisplayName("beágyazott arrow function-ök külön chunkokként jelennek meg")
    void split_realSolutionTestJs_findsNestedArrowFunctions() {
        List<String> chunks = splitter.split(SOLUTION_TEST_JS);

        // A describe callbackje + a két test callbackje.
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).contains("should correctly add two positive numbers");
        assertThat(chunks).anySatisfy(chunk -> assertThat(chunk).contains("toBe(5)"));
        assertThat(chunks).anySatisfy(chunk -> assertThat(chunk).contains("toBe(-7)"));
    }

    @Test
    @DisplayName("több-soros destructuring import eltávolítása után is parse-olható a maradék")
    void split_multilineImport_stripsCorrectly() {
        String source = """
                import {
                  add,
                  subtract
                } from "./solution";

                function useThem(a, b) {
                  return add(a, b) + subtract(a, b);
                }
                """;

        List<String> chunks = splitter.split(source);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).startsWith("function useThem(a, b)");
    }

    @Test
    @DisplayName("stringliterálban lévő kapcsos zárójel nem csúsztatja el a határokat")
    void split_braceInsideStringLiteral_noLongerAProblem() {
        String source = """
                function describeShape(name) {
                  const label = "alakzat { furcsa } jelöléssel";
                  return label + name;
                }

                function second() {
                  return 2;
                }
                """;

        List<String> chunks = splitter.split(source);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).contains("furcsa").endsWith("}");
        assertThat(chunks.get(1)).startsWith("function second()");
    }

    @Test
    @DisplayName("nem parse-olható szintaxis -> üres lista, kivétel nélkül")
    void split_unparseableSyntax_fallsBackGracefully() {
        String source = "function broken( { return ;;; }";

        assertThat(splitter.split(source)).isEmpty();
    }

    @Test
    @DisplayName("függvény nélküli fájl -> üres lista")
    void split_noFunctions_returnsEmptyList() {
        assertThat(splitter.split("const MAX = 3;\nconst MIN = 1;\n")).isEmpty();
        assertThat(splitter.split(null)).isEmpty();
        assertThat(splitter.split("  ")).isEmpty();
    }
}
