package com.legymernok.backend.integration;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A GiteaService.transformForCadetCopy() tiszta, HTTP-független logikájának
 * tesztjei — ez dönti el, mely fájlok kerülnek át a kadét saját
 * munkarepójába egy CODING misszió indításakor (lásd #47).
 */
class GiteaServiceTest {

    @Test
    void transformForCadetCopy_shouldDropRealSolutionFile() {
        Map<String, String> source = new HashMap<>();
        source.put("solution.js", "export function add(a, b) { return a + b; }");
        source.put("starter.js", "export function add(a, b) {}");

        Map<String, String> result = GiteaService.transformForCadetCopy(source);

        assertEquals(1, result.size(), "only the renamed starter file should remain");
        assertEquals("export function add(a, b) {}", result.get("solution.js"),
                "the cadet's solution.js must come from starter.js, not the real reference solution");
    }

    @Test
    void transformForCadetCopy_shouldRenameStarterFileToSolution() {
        Map<String, String> source = new HashMap<>();
        source.put("solution.py", "def add(a, b):\n    return a + b\n");
        source.put("starter.py", "def add(a, b):\n    pass\n");

        Map<String, String> result = GiteaService.transformForCadetCopy(source);

        assertTrue(result.containsKey("solution.py"));
        assertEquals("def add(a, b):\n    pass\n", result.get("solution.py"));
        assertFalse(result.containsKey("starter.py"));
    }

    @Test
    void transformForCadetCopy_shouldNotTreatTestFileAsSolutionFile() {
        // "solution.test.js" starts with "solution." but must NOT be
        // mistaken for the reference solution file — regression guard.
        Map<String, String> source = new HashMap<>();
        source.put("solution.js", "export function add(a, b) { return a + b; }");
        source.put("starter.js", "export function add(a, b) {}");
        source.put("solution.test.js", "test('adds', () => expect(add(1,2)).toBe(3));");

        Map<String, String> result = GiteaService.transformForCadetCopy(source);

        assertTrue(result.containsKey("solution.test.js"));
        assertEquals(
                "test('adds', () => expect(add(1,2)).toBe(3));",
                result.get("solution.test.js"));
    }

    @Test
    void transformForCadetCopy_shouldNotTreatPytestFileAsSolutionFile() {
        Map<String, String> source = new HashMap<>();
        source.put("solution.py", "def add(a, b):\n    return a + b\n");
        source.put("starter.py", "def add(a, b):\n    pass\n");
        source.put("test_solution.py", "from solution import add\n\ndef test_add():\n    assert add(1, 2) == 3\n");

        Map<String, String> result = GiteaService.transformForCadetCopy(source);

        assertTrue(result.containsKey("test_solution.py"));
    }

    @Test
    void transformForCadetCopy_shouldDropReadme() {
        Map<String, String> source = new HashMap<>();
        source.put("starter.js", "export function add(a, b) {}");
        source.put("README.md", "internal creator notes");

        Map<String, String> result = GiteaService.transformForCadetCopy(source);

        assertFalse(result.containsKey("README.md"));
    }

    @Test
    void transformForCadetCopy_shouldCopyOtherFilesUnchanged() {
        Map<String, String> source = new HashMap<>();
        source.put("starter.js", "export function add(a, b) {}");
        source.put("package.json", "{\"name\":\"mission\"}");
        source.put(".gitea/workflows/ci.yml", "name: verify");

        Map<String, String> result = GiteaService.transformForCadetCopy(source);

        assertEquals("{\"name\":\"mission\"}", result.get("package.json"));
        assertEquals("name: verify", result.get(".gitea/workflows/ci.yml"));
    }

    @Test
    void transformForCadetCopy_shouldPreserveDirectoryWhenRenamingStarter() {
        Map<String, String> source = new HashMap<>();
        source.put("nested/starter.py", "pass");

        Map<String, String> result = GiteaService.transformForCadetCopy(source);

        assertTrue(result.containsKey("nested/solution.py"));
    }
}
