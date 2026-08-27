package com.legymernok.backend.service.rag;

import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.fillinblank.FillInBlankDefinition;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.repository.fillinblank.FillInBlankDefinitionRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.service.ai.AiEmbeddingService;
import com.legymernok.backend.service.rag.strategy.CodeFileChunker;
import com.legymernok.backend.service.rag.strategy.TextChunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContentChunkingServiceTest {

    @Mock private MissionRepository missionRepository;
    @Mock private FillInBlankDefinitionRepository definitionRepository;
    @Mock private AiEmbeddingService embeddingService;
    @Mock private GiteaService giteaService;
    @Mock private CodeFileChunker codeFileChunker;
    @Mock private JdbcTemplate jdbcTemplate;

    // A TextChunker pure function — nincs értelme mockolni, a valódi vágást akarjuk látni.
    private final TextChunker textChunker = new TextChunker();

    private ContentChunkingService service;

    private final UUID missionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ContentChunkingService(missionRepository, definitionRepository,
                embeddingService, giteaService, codeFileChunker, textChunker, jdbcTemplate);

        when(embeddingService.toVectorString(any())).thenReturn("[0.1,0.2]");
    }

    private Mission mission(String description, String content, MissionType type) {
        Mission mission = new Mission();
        mission.setId(missionId);
        mission.setDescriptionMarkdown(description);
        mission.setContent(content);
        mission.setMissionType(type);
        return mission;
    }

    private AiEmbeddingService.Embedding embedding() {
        return new AiEmbeddingService.Embedding(new float[]{0.1f, 0.2f}, "nomic-embed-text");
    }

    @Test
    @DisplayName("minden embed sikeres -> a régi chunkok törlődnek, az újak beszúródnak")
    void reindexMission_allEmbedsSucceed_replacesOldChunks() {
        when(missionRepository.findById(missionId))
                .thenReturn(Optional.of(mission("Leírás", "Tartalom", MissionType.CONTENT)));
        when(embeddingService.embedDocument(anyString())).thenReturn(embedding());

        service.reindexMission(missionId);

        verify(jdbcTemplate).update(
                eq("DELETE FROM content_chunks WHERE source_type = ? AND source_id = ?"),
                eq("MISSION"), eq(missionId));
        verify(jdbcTemplate).batchUpdate(anyString(), any(List.class), anyInt(), any());
    }

    @Test
    @DisplayName("embed-hiba esetén a DB-hez EGYÁLTALÁN nem nyúlunk")
    void reindexMission_oneEmbedFails_keepsOldChunksUntouched() {
        // Elég hosszú szöveg ahhoz, hogy több chunkra essen — a második embed hasal el.
        String longText = "bekezdes ".repeat(120) + "\n\n" + "masik bekezdes ".repeat(120);
        when(missionRepository.findById(missionId))
                .thenReturn(Optional.of(mission(longText, null, MissionType.CONTENT)));
        when(embeddingService.embedDocument(anyString()))
                .thenReturn(embedding())
                .thenReturn(null);

        service.reindexMission(missionId);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("ismeretlen missionId -> csendben visszatér, kivétel nélkül")
    void reindexMission_missionNotFound_returnsWithoutError() {
        when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

        service.reindexMission(missionId);

        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("kiürült tartalom -> az index is kiürül (ez nem embed-hiba)")
    void reindexMission_emptyContent_clearsIndex() {
        when(missionRepository.findById(missionId))
                .thenReturn(Optional.of(mission(null, null, MissionType.CONTENT)));

        service.reindexMission(missionId);

        verify(jdbcTemplate).update(anyString(), eq("MISSION"), eq(missionId));
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(List.class), anyInt(), any());
    }

    @Test
    @DisplayName("a fill-in-blank sablonszöveg saját forrástípussal indexelődik")
    void reindexFillInBlankOnly_usesOwnSourceType() {
        FillInBlankDefinition definition = new FillInBlankDefinition();
        definition.setTemplateText("A {{1}} egy változó.");
        when(definitionRepository.findByMissionId(missionId)).thenReturn(Optional.of(definition));
        when(embeddingService.embedDocument(anyString())).thenReturn(embedding());

        service.reindexFillInBlankOnly(missionId);

        verify(jdbcTemplate).update(anyString(), eq("MISSION_FILL_IN_BLANK"), eq(missionId));
    }

    @Test
    @DisplayName("a referencia megoldás AUTHOR_ONLY, a starter PUBLIC láthatóságot kap")
    void reindexCodingMissionFiles_marksSolutionAuthorOnly() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("solution.js", "function add(a,b){return a+b;}");
        files.put("starter.js", "function add(a,b){}");
        files.put("README.md", "# Doksi");

        when(codeFileChunker.isIndexableSourceFile("solution.js")).thenReturn(true);
        when(codeFileChunker.isIndexableSourceFile("starter.js")).thenReturn(true);
        when(codeFileChunker.isIndexableSourceFile("README.md")).thenReturn(false);
        when(codeFileChunker.chunkFile(anyString(), anyString()))
                .thenAnswer(call -> List.of(call.getArgument(1, String.class)));
        when(embeddingService.embedDocument(anyString())).thenReturn(embedding());

        service.reindexCodingMissionFiles(missionId, files);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContentChunkingService.PendingChunk>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(anyString(), captor.capture(), anyInt(), any());

        assertThat(captor.getValue())
                .as("a README.md nem indexelhető, tehát nem kerül be")
                .hasSize(2)
                .anySatisfy(chunk -> {
                    assertThat(chunk.filePath()).isEqualTo("solution.js");
                    assertThat(chunk.visibility()).isEqualTo("AUTHOR_ONLY");
                })
                .anySatisfy(chunk -> {
                    assertThat(chunk.filePath()).isEqualTo("starter.js");
                    assertThat(chunk.visibility()).isEqualTo("PUBLIC");
                });
    }

    @Test
    @DisplayName("a második fájl embed-hibája is megőrzi a teljes régi kódindexet")
    void reindexCodingMissionFiles_oneEmbedFailsInSecondFile_keepsAllOldChunksUntouched() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("a_first.js", "function a(){}");
        files.put("b_second.js", "function b(){}");

        when(codeFileChunker.isIndexableSourceFile(anyString())).thenReturn(true);
        when(codeFileChunker.chunkFile(anyString(), anyString()))
                .thenAnswer(call -> List.of(call.getArgument(1, String.class)));
        when(embeddingService.embedDocument(anyString()))
                .thenReturn(embedding())
                .thenReturn(null);

        service.reindexCodingMissionFiles(missionId, files);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("reindexAllMissions minden missziót feldolgoz, és a típusnak megfelelő ágat is futtatja")
    void reindexAllMissions_countsProcessedMissions() {
        Mission content = mission("A", null, MissionType.CONTENT);
        Mission fillInBlank = new Mission();
        fillInBlank.setId(UUID.randomUUID());
        fillInBlank.setMissionType(MissionType.FILL_IN_BLANK);
        Mission coding = new Mission();
        coding.setId(UUID.randomUUID());
        coding.setMissionType(MissionType.CODING);

        when(missionRepository.findAll()).thenReturn(List.of(content, fillInBlank, coding));
        when(missionRepository.findById(any())).thenReturn(Optional.empty());
        when(definitionRepository.findByMissionId(any())).thenReturn(Optional.empty());
        when(giteaService.getAdminUsername()).thenReturn("legymernok_admin");
        when(giteaService.collectAllFiles(anyString(), anyString())).thenReturn(Map.of());

        assertThat(service.reindexAllMissions()).isEqualTo(3);

        verify(giteaService).collectAllFiles("legymernok_admin", coding.getId().toString());
    }
}
