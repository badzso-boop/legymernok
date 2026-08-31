package com.legymernok.backend.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A séma-szintű állítások ellenőrzése egy VALÓDI Postgres ellen.
 *
 * <p><b>Miért kell külön ehhez egy konténer:</b> a projekt összes többi backend-tesztje
 * Mockito-alapú, tehát egyetlen sor SQL sem fut le bennük — semmi nem bizonyítja, hogy a
 * migrációk egyáltalán szintaktikailag helyesek, hogy az FK tényleg kaszkádol, vagy hogy a
 * {@code 'hungarian'} szövegkeresési konfiguráció tényleg szótövez. Ez a hibaosztály ebben a
 * repóban már háromszor előfordult (ld. a gyökér {@code CLAUDE.md}-t), ezért nem elég a
 * migrációt "elolvasni".
 *
 * <p><b>Az image nem cserélhető {@code postgres:16}-ra:</b> a {@code vector} extension csak a
 * pgvector-image-ben van benne — pont ez az egyik állítás, amit itt ellenőrzünk.
 *
 * <p>Szándékosan NINCS Spring-kontextus: a Flyway-t közvetlenül futtatjuk, és nyers JDBC-vel
 * állítunk. Így a teszt azt méri, amit mérni akar (a séma viselkedését), nem a
 * konténer-bootstrapet.
 */
class ContentChunkSchemaIT {

    private static DockerPostgres postgres;

    @BeforeAll
    static void startDatabaseAndMigrate() {
        postgres = DockerPostgres.start();
        Flyway.configure()
                .dataSource(postgres.jdbcUrl(), postgres.username(), postgres.password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate();
    }

    @AfterAll
    static void stopDatabase() {
        if (postgres != null) postgres.stop();
    }

    private Connection connection() throws SQLException {
        return postgres.connect();
    }

    /** Egy misszió (és a hozzá szükséges csillagrendszer) beszúrása, a misszió ID-jával. */
    private UUID insertMission(Connection conn, String suffix) throws SQLException {
        UUID starSystemId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO star_systems (id, name) VALUES (?, ?)")) {
            ps.setObject(1, starSystemId);
            ps.setString(2, "Teszt rendszer " + suffix);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO missions (id, star_system_id, name, mission_type, difficulty)
                VALUES (?, ?, ?, 'CONTENT', 'EASY')
                """)) {
            ps.setObject(1, missionId);
            ps.setObject(2, starSystemId);
            ps.setString(3, "Teszt misszió " + suffix);
            ps.executeUpdate();
        }
        return missionId;
    }

    private void insertChunk(Connection conn, UUID missionId, String filePath, int index,
                             String text) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO content_chunks
                    (source_type, source_id, file_path, chunk_index, chunk_text, embedding_model)
                VALUES ('MISSION', ?, ?, ?, ?, 'nomic-embed-text')
                """)) {
            ps.setObject(1, missionId);
            ps.setString(2, filePath);
            ps.setInt(3, index);
            ps.setString(4, text);
            ps.executeUpdate();
        }
    }

    private long countChunks(Connection conn, UUID missionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*) FROM content_chunks WHERE source_id = ?")) {
            ps.setObject(1, missionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    @DisplayName("V1…V10 hibátlanul lefut egy tiszta adatbázison")
    void allMigrationsApplyOnCleanDatabase() throws SQLException {
        try (Connection conn = connection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT count(*) FILTER (WHERE success IS FALSE) AS failed,
                            max(version::int) AS latest
                     FROM flyway_schema_history
                     WHERE version IS NOT NULL
                     """)) {
            rs.next();
            assertThat(rs.getInt("failed")).as("bukott migráció").isZero();
            assertThat(rs.getInt("latest")).as("legmagasabb alkalmazott verzió").isEqualTo(10);
        }
    }

    @Test
    @DisplayName("a vector extension elérhető, és a gen_random_uuid() működik")
    void vectorExtensionAndUuidGenerationAvailable() throws SQLException {
        try (Connection conn = connection(); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM pg_extension WHERE extname = 'vector'")) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("a vector extension csak a pgvector image-ben van benne")
                        .isEqualTo(1);
            }
            // A migrációk gen_random_uuid()-t használnak DEFAULT-ként. Ez PG13 óta beépített
            // (nem kell hozzá pgcrypto) — ezt bizonyítjuk, nem az extension meglétét.
            try (ResultSet rs = st.executeQuery("SELECT gen_random_uuid()")) {
                rs.next();
                assertThat(rs.getString(1)).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("misszió törlése magával viszi a chunkjait (FK ON DELETE CASCADE)")
    void deletingMissionCascadesItsChunks() throws SQLException {
        try (Connection conn = connection()) {
            UUID missionId = insertMission(conn, "cascade");
            insertChunk(conn, missionId, "", 0, "első darab");
            insertChunk(conn, missionId, "", 1, "második darab");

            assertThat(countChunks(conn, missionId)).isEqualTo(2);

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM missions WHERE id = ?")) {
                ps.setObject(1, missionId);
                ps.executeUpdate();
            }

            assertThat(countChunks(conn, missionId))
                    .as("a takarítást az adatbázisnak kell végeznie, nem egy service-hívásnak")
                    .isZero();
        }
    }

    @Test
    @DisplayName("a unique constraint üres file_path mellett is fog")
    void uniqueChunkConstraintHoldsWithEmptyFilePath() throws SQLException {
        try (Connection conn = connection()) {
            UUID missionId = insertMission(conn, "unique");
            insertChunk(conn, missionId, "", 0, "eredeti");

            // Ha a file_path NULL-abilis lenne, ez a második INSERT csendben átmenne
            // (két NULL sose egyenlő egy UNIQUE constraintben).
            assertThatThrownBy(() -> insertChunk(conn, missionId, "", 0, "duplikátum"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("content_chunks_unique_chunk");
        }
    }

    @Test
    @DisplayName("a hungarian tsvector szótövezi a ragozott alakot")
    void hungarianTsvectorStemsInflectedForms() throws SQLException {
        try (Connection conn = connection()) {
            UUID missionId = insertMission(conn, "tsvector");
            insertChunk(conn, missionId, "", 0, "A feladatban a függvényt hívjuk meg kétszer.");

            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT count(*) FROM content_chunks
                    WHERE source_id = ?
                      AND search_vector @@ plainto_tsquery('hungarian', 'függvény')
                    """)) {
                ps.setObject(1, missionId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getLong(1))
                            .as("'függvényt' -> 'függvény': ezt a 'simple' konfiguráció NEM tudná")
                            .isEqualTo(1);
                }
            }
        }
    }

    @Test
    @DisplayName("embedding_model nélküli beszúrás elszáll")
    void embeddingModelColumnIsRequired() throws SQLException {
        try (Connection conn = connection()) {
            UUID missionId = insertMission(conn, "model");

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO content_chunks (source_type, source_id, chunk_index, chunk_text)
                        VALUES ('MISSION', ?, 0, 'nincs modellnév')
                        """)) {
                    ps.setObject(1, missionId);
                    ps.executeUpdate();
                }
            })
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("embedding_model");
        }
    }

    @Test
    @DisplayName("a visibility CHECK csak a két ismert értéket engedi")
    void visibilityCheckRejectsUnknownValues() throws SQLException {
        try (Connection conn = connection()) {
            UUID missionId = insertMission(conn, "visibility");

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO content_chunks
                            (source_type, source_id, chunk_index, chunk_text, embedding_model, visibility)
                        VALUES ('MISSION', ?, 0, 'szöveg', 'nomic-embed-text', 'MINDENKI')
                        """)) {
                    ps.setObject(1, missionId);
                    ps.executeUpdate();
                }
            })
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("content_chunks_visibility_check");
        }
    }
}
