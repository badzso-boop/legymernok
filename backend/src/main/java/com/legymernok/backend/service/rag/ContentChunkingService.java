package com.legymernok.backend.service.rag;

import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.repository.fillinblank.FillInBlankDefinitionRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.service.ai.AiEmbeddingService;
import com.legymernok.backend.service.mission.MissionFilePatterns;
import com.legymernok.backend.service.rag.strategy.CodeFileChunker;
import com.legymernok.backend.service.rag.strategy.TextChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A RAG chunk-index karbantartása: szöveg/kód darabolása, beágyazása és a
 * {@code content_chunks} táblába írása.
 *
 * <p>Nincs JPA entitás ehhez a táblához — közvetlen {@link JdbcTemplate}, a
 * {@code StarSystemService} meglévő pgvector-mintáját követve.
 *
 * <p><b>Embed-first sorrend (a legfontosabb szabály itt):</b> előbb MINDEN chunk sikeresen
 * beágyazódik, és csak teljes sikeren töröljük a régieket + írjuk be az újakat. A fordított
 * sorrend (törlés, majd embedelés) egy átmeneti ai-service-kiesésnél <b>csendben, részlegesen
 * indexelt</b> állapotot hagyna: a hiba semmiben nem látszana, csak rosszabb chat-válaszokban.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentChunkingService {

    public static final String SOURCE_MISSION = "MISSION";
    public static final String SOURCE_FILL_IN_BLANK = "MISSION_FILL_IN_BLANK";
    public static final String SOURCE_CODE_FILE = "MISSION_CODE_FILE";

    static final String VISIBILITY_PUBLIC = "PUBLIC";
    static final String VISIBILITY_AUTHOR_ONLY = "AUTHOR_ONLY";

    /** A fájlhoz nem köthető chunkok {@code file_path}-ja — NOT NULL, ld. a V10 migrációt. */
    private static final String NO_FILE = "";

    private final MissionRepository missionRepository;
    private final FillInBlankDefinitionRepository definitionRepository;
    private final AiEmbeddingService embeddingService;
    private final GiteaService giteaService;
    private final CodeFileChunker codeFileChunker;
    private final TextChunker textChunker;
    private final JdbcTemplate jdbcTemplate;

    /** Bekezdés-alapú szövegvágás — a {@link TextChunker} felé delegál. */
    public List<String> chunkText(String text) {
        return textChunker.chunk(text);
    }

    /**
     * Misszió leírásának/tartalmának újraindexelése ({@code MISSION} forrástípus).
     *
     * <p>Ismeretlen {@code missionId} esetén csendben visszatér: ez háttér-hívás egy másik
     * service metódusából, nem önálló, felhasználó-facing endpoint.
     */
    @Transactional
    public void reindexMission(UUID missionId) {
        Mission mission = missionRepository.findById(missionId).orElse(null);
        if (mission == null) return;

        List<String> chunks = textChunker.chunk(buildMissionText(mission));
        replaceChunks(SOURCE_MISSION, missionId, chunks, NO_FILE, VISIBILITY_PUBLIC);
    }

    /** A fill-in-blank sablonszöveg újraindexelése ({@code MISSION_FILL_IN_BLANK}). */
    @Transactional
    public void reindexFillInBlankOnly(UUID missionId) {
        String templateText = definitionRepository.findByMissionId(missionId)
                .map(definition -> definition.getTemplateText())
                .orElse(null);

        List<String> chunks = textChunker.chunk(templateText);
        replaceChunks(SOURCE_FILL_IN_BLANK, missionId, chunks, NO_FILE, VISIBILITY_PUBLIC);
    }

    /**
     * CODING-misszió kódfájljainak újraindexelése a már memóriában lévő fájl-map-ből
     * (Forge-mentés útvonala — nincs szükség extra Gitea-hívásra).
     */
    @Transactional
    public void reindexCodingMissionFiles(UUID missionId, Map<String, String> files) {
        List<PendingChunk> pending = embedCodeFiles(missionId, files);
        if (pending == null) return;

        deleteChunks(SOURCE_CODE_FILE, missionId);
        insertChunks(SOURCE_CODE_FILE, missionId, pending);
        log.info("RAG index frissítve: mission {} kódfájljai -> {} chunk", missionId, pending.size());
    }

    /**
     * Ugyanaz, de a fájlokat a Gitea-ból tölti le (a tömeges újraindexelés útvonala, ahol
     * nincs memóriában lévő fájl-map).
     */
    @Transactional
    public void reindexCodingMissionFilesFromGitea(UUID missionId) {
        Map<String, String> files = giteaService.collectAllFiles(
                giteaService.getAdminUsername(), missionId.toString());
        reindexCodingMissionFiles(missionId, files);
    }

    @Transactional
    public void deleteChunks(String sourceType, UUID sourceId) {
        jdbcTemplate.update(
                "DELETE FROM content_chunks WHERE source_type = ? AND source_id = ?",
                sourceType, sourceId);
    }

    /**
     * Minden misszió újraindexelése — a {@code StarSystemService.reindexAllStarSystems()}
     * mintáját tükrözi.
     *
     * @return a feldolgozott missziók száma
     */
    @Transactional
    public int reindexAllMissions() {
        List<Mission> all = missionRepository.findAll();
        for (Mission mission : all) {
            reindexMission(mission.getId());
            if (mission.getMissionType() == MissionType.FILL_IN_BLANK) {
                reindexFillInBlankOnly(mission.getId());
            } else if (mission.getMissionType() == MissionType.CODING) {
                reindexCodingMissionFilesFromGitea(mission.getId());
            }
        }
        log.info("RAG reindex kész: {} misszió feldolgozva", all.size());
        return all.size();
    }

    // ---------------------------------------------------------------------------------

    private String buildMissionText(Mission mission) {
        StringBuilder text = new StringBuilder();
        if (mission.getDescriptionMarkdown() != null) {
            text.append(mission.getDescriptionMarkdown());
        }
        if (mission.getContent() != null) {
            if (text.length() > 0) text.append("\n\n");
            text.append(mission.getContent());
        }
        return text.toString();
    }

    /**
     * Egyetlen, fájlhoz nem kötött forrástípus teljes cseréje.
     *
     * <p>Üres chunk-lista esetén az index kiürül — ez NEM embed-hiba, hanem szándékos
     * állapot (valaki kitörölte a tartalmat), tehát itt nem alkalmazandó az "őrizzük meg a
     * régit" szabály.
     */
    private void replaceChunks(String sourceType, UUID sourceId, List<String> chunks,
                               String filePath, String visibility) {
        if (chunks.isEmpty()) {
            deleteChunks(sourceType, sourceId);
            return;
        }

        List<PendingChunk> pending = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            PendingChunk embedded = embed(chunks.get(i), filePath, i, visibility);
            if (embedded == null) {
                log.warn("Embedding failed for {} {} chunk {}/{} - reindex megszakítva, "
                        + "a régi index-állapot változatlan marad",
                        sourceType, sourceId, i, chunks.size());
                return;
            }
            pending.add(embedded);
        }

        deleteChunks(sourceType, sourceId);
        insertChunks(sourceType, sourceId, pending);
        log.info("RAG index frissítve: {} {} -> {} chunk", sourceType, sourceId, pending.size());
    }

    /**
     * A kódfájlok beágyazása.
     *
     * @return a beszúrásra kész chunkok, vagy {@code null}, ha bármelyik fájl bármelyik
     *         chunkjának beágyazása elhasalt (ilyenkor a hívó a DB-hez nem nyúl)
     */
    private List<PendingChunk> embedCodeFiles(UUID missionId, Map<String, String> files) {
        List<PendingChunk> pending = new ArrayList<>();
        if (files == null) return pending;

        // Rendezett bejárás: a chunk_index így determinisztikus, két egymás utáni reindex
        // ugyanazt az indexelést adja.
        for (Map.Entry<String, String> entry : new TreeMap<>(files).entrySet()) {
            String path = entry.getKey();
            if (!codeFileChunker.isIndexableSourceFile(path)) continue;

            String visibility = isReferenceSolution(path) ? VISIBILITY_AUTHOR_ONLY : VISIBILITY_PUBLIC;
            List<String> chunks = codeFileChunker.chunkFile(path, entry.getValue());

            for (int i = 0; i < chunks.size(); i++) {
                PendingChunk embedded = embed(chunks.get(i), path, i, visibility);
                if (embedded == null) {
                    log.warn("Embedding failed for mission {} file {} chunk {}/{} - "
                            + "kódindex-frissítés megszakítva, a régi állapot változatlan",
                            missionId, path, i, chunks.size());
                    return null;
                }
                pending.add(embedded);
            }
        }
        return pending;
    }

    /**
     * A referencia megoldás soha nem kerülhet kadét kontextusába. A mintát a
     * {@link MissionFilePatterns} adja — ugyanaz, ami a kadét-másolatból is kihagyja a
     * fájlt: két külön lista szétcsúszása csendes hiba lenne.
     */
    private boolean isReferenceSolution(String path) {
        int slash = path.lastIndexOf('/');
        String fileName = slash < 0 ? path : path.substring(slash + 1);
        return MissionFilePatterns.SOLUTION.matcher(fileName).matches();
    }

    private PendingChunk embed(String text, String filePath, int index, String visibility) {
        AiEmbeddingService.Embedding embedding = embeddingService.embedDocument(text);
        if (embedding == null) return null;
        return new PendingChunk(filePath, index, text,
                embeddingService.toVectorString(embedding.vector()), embedding.model(), visibility);
    }

    private void insertChunks(String sourceType, UUID sourceId, List<PendingChunk> chunks) {
        if (chunks.isEmpty()) return;
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO content_chunks
                    (source_type, source_id, file_path, chunk_index, chunk_text,
                     content_embedding, embedding_model, visibility)
                VALUES (?, ?, ?, ?, ?, ?::vector, ?, ?)
                """,
                chunks, chunks.size(),
                (ps, chunk) -> {
                    ps.setString(1, sourceType);
                    ps.setObject(2, sourceId);
                    ps.setString(3, chunk.filePath());
                    ps.setInt(4, chunk.index());
                    ps.setString(5, chunk.text());
                    ps.setString(6, chunk.vectorStr());
                    ps.setString(7, chunk.model());
                    ps.setString(8, chunk.visibility());
                });
    }

    record PendingChunk(String filePath, int index, String text, String vectorStr,
                        String model, String visibility) {}
}
