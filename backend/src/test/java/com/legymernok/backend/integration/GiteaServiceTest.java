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

        Map<String, String> result = GiteaService.transformForCadetCopy(source, "606ac43b-f91b-4fdb-a1db-98299c94babe");

        assertEquals(1, result.size(), "only the renamed starter file should remain");
        assertEquals("export function add(a, b) {}", result.get("solution.js"),
                "the cadet's solution.js must come from starter.js, not the real reference solution");
    }

    @Test
    void transformForCadetCopy_shouldRenameStarterFileToSolution() {
        Map<String, String> source = new HashMap<>();
        source.put("solution.py", "def add(a, b):\n    return a + b\n");
        source.put("starter.py", "def add(a, b):\n    pass\n");

        Map<String, String> result = GiteaService.transformForCadetCopy(source, "606ac43b-f91b-4fdb-a1db-98299c94babe");

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

        Map<String, String> result = GiteaService.transformForCadetCopy(source, "606ac43b-f91b-4fdb-a1db-98299c94babe");

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

        Map<String, String> result = GiteaService.transformForCadetCopy(source, "606ac43b-f91b-4fdb-a1db-98299c94babe");

        assertTrue(result.containsKey("test_solution.py"));
    }

    @Test
    void transformForCadetCopy_shouldDropReadme() {
        Map<String, String> source = new HashMap<>();
        source.put("starter.js", "export function add(a, b) {}");
        source.put("README.md", "internal creator notes");

        Map<String, String> result = GiteaService.transformForCadetCopy(source, "606ac43b-f91b-4fdb-a1db-98299c94babe");

        assertFalse(result.containsKey("README.md"));
    }

    @Test
    void transformForCadetCopy_shouldCopyOtherFilesUnchanged() {
        Map<String, String> source = new HashMap<>();
        source.put("starter.js", "export function add(a, b) {}");
        source.put("package.json", "{\"name\":\"mission\"}");
        source.put(".gitea/workflows/ci.yml", "name: verify");

        Map<String, String> result = GiteaService.transformForCadetCopy(source, "606ac43b-f91b-4fdb-a1db-98299c94babe");

        assertEquals("{\"name\":\"mission\"}", result.get("package.json"));
        assertEquals("name: verify", result.get(".gitea/workflows/ci.yml"));
    }

    @Test
    void transformForCadetCopy_shouldPreserveDirectoryWhenRenamingStarter() {
        Map<String, String> source = new HashMap<>();
        source.put("nested/starter.py", "pass");

        Map<String, String> result = GiteaService.transformForCadetCopy(source, "606ac43b-f91b-4fdb-a1db-98299c94babe");

        assertTrue(result.containsKey("nested/solution.py"));
    }

    @Test
    void transformForCadetCopy_whenNoStarterFile_shouldStillCreateEmptySolutionForLegacyMissions() {
        // Missziók, amiket a starter.<ext> konvenció bevezetése ELŐTT hoztak
        // létre — nincs starter.js-ük, csak solution.js + solution.test.js.
        // A kadét repójában így is kell legyen egy (üres) solution.js, amit
        // a teszt importálhat — enélkül CI hibára futna minden meglévő
        // misszión (regresszió, amit a review talált).
        Map<String, String> source = new HashMap<>();
        source.put("solution.js", "export function add(a, b) { return a + b; }");
        source.put("solution.test.js", "test('adds', () => {});");

        Map<String, String> result = GiteaService.transformForCadetCopy(source, "606ac43b-f91b-4fdb-a1db-98299c94babe");

        assertTrue(result.containsKey("solution.js"));
        assertEquals("", result.get("solution.js"), "the real reference solution must never leak, even as a fallback");
        assertTrue(result.containsKey("solution.test.js"));
    }

    @Test
    void transformForCadetCopy_shouldMatchSolutionAndStarterFilesCaseInsensitively() {
        Map<String, String> source = new HashMap<>();
        source.put("Solution.JS", "export function add(a, b) { return a + b; }");
        source.put("Starter.JS", "export function add(a, b) {}");

        Map<String, String> result = GiteaService.transformForCadetCopy(source, "606ac43b-f91b-4fdb-a1db-98299c94babe");

        assertEquals(1, result.size());
        assertEquals("export function add(a, b) {}", result.get("solution.js"));
    }

    @Test
    void transformForCadetCopy_shouldDropReadmeCaseInsensitively() {
        Map<String, String> source = new HashMap<>();
        source.put("starter.js", "export function add(a, b) {}");
        source.put("readme.md", "internal creator notes");

        Map<String, String> result = GiteaService.transformForCadetCopy(source, "606ac43b-f91b-4fdb-a1db-98299c94babe");

        assertFalse(result.containsKey("readme.md"));
    }

    @Test
    void transformForCadetCopy_shouldRewriteCiYmlMissionIdToActualMissionId() {
        // A ci.yml sablonban a mission_id `${{ github.event.repository.name }}`-ből jön,
        // ami az admin saját (Forge) repójánál helyes (repónév == Mission UUID), de a
        // kadét "cadet-<username>-<missionId>" nevű repójában a callback URL-be a teljes
        // repónevet írná be a valódi Mission UUID helyett (#52) — ezt itt cseréljük le.
        Map<String, String> source = new HashMap<>();
        source.put(".gitea/workflows/ci.yml",
                "jobs:\n  verify:\n    steps:\n      - uses: mission-verifier/actions@main\n        with:\n          mission_id: ${{ github.event.repository.name }}\n");

        Map<String, String> result = GiteaService.transformForCadetCopy(source, "606ac43b-f91b-4fdb-a1db-98299c94babe");

        assertTrue(result.get(".gitea/workflows/ci.yml").contains("mission_id: 606ac43b-f91b-4fdb-a1db-98299c94babe"));
        assertFalse(result.get(".gitea/workflows/ci.yml").contains("github.event.repository.name"));
    }
}
