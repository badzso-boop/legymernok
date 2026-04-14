package com.legymernok.backend.service.fillinblank;

import com.legymernok.backend.dto.fillinblank.*;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.fillinblank.*;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionGroup;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.fillinblank.*;
import com.legymernok.backend.repository.mission.MissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FillInBlankServiceTest {

    @Mock private MissionRepository missionRepository;
    @Mock private FillInBlankDefinitionRepository definitionRepository;
    @Mock private FillInBlankBlankRepository blankRepository;
    @Mock private FillInBlankOptionRepository optionRepository;
    @Mock private FillInBlankAttemptRepository attemptRepository;
    @Mock private FillInBlankAnswerDetailRepository answerDetailRepository;
    @Mock private CadetRepository cadetRepository;
    @InjectMocks private FillInBlankService fillInBlankService;

    private Cadet testUser;
    private Mission fibMission;
    private MissionGroup testGroup;

    @BeforeEach
    void setUp() {
        testUser = new Cadet();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("test_user");

        testGroup = MissionGroup.builder()
                .id(UUID.randomUUID())
                .name("Test Group")
                .orderIndex(1)
                .build();

        fibMission = new Mission();
        fibMission.setId(UUID.randomUUID());
        fibMission.setMissionType(MissionType.FILL_IN_BLANK);
        fibMission.setGroup(testGroup);

        Authentication auth = mock(Authentication.class);
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
        lenient().when(auth.getName()).thenReturn("test_user");
        lenient().when(cadetRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
    }

    // =========================================================================
    // saveDefinition tesztek
    // =========================================================================

    @Test
    void saveDefinition_whenMissionIsStandalone_shouldThrowConflict() {
        Mission standaloneMission = new Mission();
        standaloneMission.setId(UUID.randomUUID());
        standaloneMission.setMissionType(MissionType.FILL_IN_BLANK);
        standaloneMission.setGroup(null); // standalone

        when(missionRepository.findById(standaloneMission.getId()))
                .thenReturn(Optional.of(standaloneMission));

        SaveFillInBlankRequest request = new SaveFillInBlankRequest();
        request.setTemplateText("Fill: {{blank_1}}");
        request.setBlanks(List.of());

        assertThrows(ResourceConflictException.class,
                () -> fillInBlankService.saveDefinition(standaloneMission.getId(), request));
    }

    @Test
    void saveDefinition_whenDefinitionExists_shouldDeleteOldAndSaveNew() {
        UUID missionId = fibMission.getId();

        FillInBlankDefinition existingDef = FillInBlankDefinition.builder()
                .id(UUID.randomUUID())
                .mission(fibMission)
                .templateText("Old text")
                .build();

        FillInBlankBlank existingBlank = FillInBlankBlank.builder()
                .id(UUID.randomUUID())
                .definition(existingDef)
                .blanksKey("blank_1")
                .orderIndex(0)
                .build();

        FillInBlankOption existingOption = FillInBlankOption.builder()
                .id(UUID.randomUUID())
                .blank(existingBlank)
                .optionText("old option")
                .correct(true)
                .orderIndex(0)
                .build();

        FillInBlankDefinition newDef = FillInBlankDefinition.builder()
                .id(UUID.randomUUID())
                .mission(fibMission)
                .templateText("New text {{blank_1}}")
                .build();

        FillInBlankBlank newBlank = FillInBlankBlank.builder()
                .id(UUID.randomUUID())
                .definition(newDef)
                .blanksKey("blank_1")
                .orderIndex(0)
                .build();

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(fibMission));
        when(definitionRepository.findByMissionId(missionId))
                .thenReturn(Optional.of(existingDef))   // 1. hívás: delete path
                .thenReturn(Optional.of(newDef));        // 2. hívás: getDefinitionForUser
        when(blankRepository.findAllByDefinitionIdOrderByOrderIndexAsc(existingDef.getId()))
                .thenReturn(List.of(existingBlank));
        when(optionRepository.findAllByBlankIdOrderByOrderIndexAsc(existingBlank.getId()))
                .thenReturn(List.of(existingOption));
        when(definitionRepository.save(any())).thenReturn(newDef);
        when(blankRepository.save(any())).thenReturn(newBlank);
        when(blankRepository.findAllByDefinitionIdOrderByOrderIndexAsc(newDef.getId()))
                .thenReturn(List.of(newBlank));
        when(optionRepository.findAllByBlankIdOrderByOrderIndexAsc(newBlank.getId()))
                .thenReturn(List.of());

        SaveFillInBlankRequest.OptionRequest optReq = new SaveFillInBlankRequest.OptionRequest();
        optReq.setOptionText("New option");
        optReq.setCorrect(true);
        optReq.setOrderIndex(0);

        SaveFillInBlankRequest.BlankRequest blankReq = new SaveFillInBlankRequest.BlankRequest();
        blankReq.setKey("blank_1");
        blankReq.setOrderIndex(0);
        blankReq.setOptions(List.of(optReq));

        SaveFillInBlankRequest request = new SaveFillInBlankRequest();
        request.setTemplateText("New text {{blank_1}}");
        request.setBlanks(List.of(blankReq));

        fillInBlankService.saveDefinition(missionId, request);

        // Régi entitások törlése
        verify(optionRepository).delete(existingOption);
        verify(blankRepository).delete(existingBlank);
        verify(definitionRepository).delete(existingDef);

        // Új definíció mentése
        verify(definitionRepository).save(any(FillInBlankDefinition.class));
        verify(blankRepository).save(any(FillInBlankBlank.class));
        verify(optionRepository).save(any(FillInBlankOption.class));
    }

    // =========================================================================
    // submit tesztek
    // =========================================================================

    @Test
    void submit_whenAllAnswersCorrect_withThreshold70_shouldPass() {
        UUID missionId = fibMission.getId();

        FillInBlankDefinition def = FillInBlankDefinition.builder()
                .id(UUID.randomUUID()).mission(fibMission).templateText("test")
                .passThreshold(70).build();

        FillInBlankBlank blank1 = buildBlank(def, "blank_1", 0);
        FillInBlankBlank blank2 = buildBlank(def, "blank_2", 1);

        UUID opt1Id = UUID.randomUUID();
        UUID opt2Id = UUID.randomUUID();
        FillInBlankOption opt1 = buildOption(blank1, "A", true, opt1Id);
        FillInBlankOption opt2 = buildOption(blank2, "X", true, opt2Id);

        setupSubmitMocks(missionId, def, List.of(blank1, blank2), List.of(opt1, opt2));

        SubmitFillInBlankRequest request = new SubmitFillInBlankRequest();
        request.setAnswers(Map.of("blank_1", opt1Id, "blank_2", opt2Id));

        FillInBlankResultResponse result = fillInBlankService.submit(missionId, request);

        assertEquals(2, result.getScore());
        assertEquals(2, result.getMaxScore());
        assertEquals(100, result.getPercentage());
        assertTrue(result.isPassed());
    }

    @Test
    void submit_whenOneAnswerWrong_withThreshold70_shouldFail() {
        UUID missionId = fibMission.getId();

        FillInBlankDefinition def = FillInBlankDefinition.builder()
                .id(UUID.randomUUID()).mission(fibMission).templateText("test")
                .passThreshold(70).build();

        FillInBlankBlank blank1 = buildBlank(def, "blank_1", 0);
        FillInBlankBlank blank2 = buildBlank(def, "blank_2", 1);

        UUID opt1CorrectId = UUID.randomUUID();
        UUID opt2WrongId = UUID.randomUUID();
        FillInBlankOption opt1Correct = buildOption(blank1, "A", true, opt1CorrectId);
        FillInBlankOption opt2Wrong = buildOption(blank2, "Wrong", false, opt2WrongId);
        FillInBlankOption opt2Correct = buildOption(blank2, "Correct", true, UUID.randomUUID());

        setupSubmitMocks(missionId, def, List.of(blank1, blank2),
                List.of(opt1Correct, opt2Wrong, opt2Correct));

        SubmitFillInBlankRequest request = new SubmitFillInBlankRequest();
        request.setAnswers(Map.of("blank_1", opt1CorrectId, "blank_2", opt2WrongId));

        FillInBlankResultResponse result = fillInBlankService.submit(missionId, request);

        assertEquals(1, result.getScore());
        assertEquals(50, result.getPercentage());
        assertFalse(result.isPassed());
    }

    @Test
    void submit_whenPassThresholdIsNull_shouldAlwaysPass() {
        UUID missionId = fibMission.getId();

        FillInBlankDefinition def = FillInBlankDefinition.builder()
                .id(UUID.randomUUID()).mission(fibMission).templateText("test")
                .passThreshold(null).build(); // nincs küszöb

        FillInBlankBlank blank1 = buildBlank(def, "blank_1", 0);
        UUID wrongOptId = UUID.randomUUID();
        FillInBlankOption wrongOpt = buildOption(blank1, "Wrong", false, wrongOptId);

        setupSubmitMocks(missionId, def, List.of(blank1), List.of(wrongOpt));

        SubmitFillInBlankRequest request = new SubmitFillInBlankRequest();
        request.setAnswers(Map.of("blank_1", wrongOptId));

        FillInBlankResultResponse result = fillInBlankService.submit(missionId, request);

        assertTrue(result.isPassed(), "Ha passThreshold null, mindig passed=true");
    }

    // =========================================================================
    // getDefinitionForUser — correct mező biztonsági teszt
    // =========================================================================

    @Test
    void getDefinitionForUser_shouldNotExposeCorrectField() throws Exception {
        // Reflection-nel ellenőrizzük, hogy az OptionResponse osztálynak nincs 'correct' mezője
        Class<?> optionResponseClass = FillInBlankUserResponse.OptionResponse.class;

        boolean hasCorrectField = Arrays.stream(optionResponseClass.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("correct"));

        assertFalse(hasCorrectField,
                "FillInBlankUserResponse.OptionResponse nem tartalmazhat 'correct' mezőt");
    }

    // =========================================================================
    // getLastAttempt teszt
    // =========================================================================

    @Test
    void getLastAttempt_whenNoAttemptExists_shouldReturnEmpty() {
        UUID missionId = fibMission.getId();

        when(attemptRepository.findTopByCadetIdAndMissionIdOrderBySubmittedAtDesc(testUser.getId(), missionId))
                .thenReturn(Optional.empty());

        Optional<LastAttemptResponse> result = fillInBlankService.getLastAttempt(missionId);

        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // Helper metódusok
    // =========================================================================

    private void setupSubmitMocks(UUID missionId, FillInBlankDefinition def,
                                   List<FillInBlankBlank> blanks, List<FillInBlankOption> options) {
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(fibMission));
        when(definitionRepository.findByMissionId(missionId)).thenReturn(Optional.of(def));
        when(blankRepository.findAllByDefinitionIdOrderByOrderIndexAsc(def.getId())).thenReturn(blanks);
        when(optionRepository.findAllByDefinitionId(def.getId())).thenReturn(options);

        FillInBlankAttempt savedAttempt = FillInBlankAttempt.builder()
                .id(UUID.randomUUID()).cadet(testUser).mission(fibMission)
                .score(0).maxScore(blanks.size()).percentage(0).passed(false).build();
        when(attemptRepository.save(any(FillInBlankAttempt.class))).thenReturn(savedAttempt);
        when(answerDetailRepository.saveAll(any())).thenReturn(List.of());
    }

    private FillInBlankBlank buildBlank(FillInBlankDefinition def, String key, int order) {
        return FillInBlankBlank.builder()
                .id(UUID.randomUUID()).definition(def).blanksKey(key).orderIndex(order).build();
    }

    private FillInBlankOption buildOption(FillInBlankBlank blank, String text, boolean correct, UUID id) {
        return FillInBlankOption.builder()
                .id(id).blank(blank).optionText(text).correct(correct).orderIndex(0).build();
    }
}
