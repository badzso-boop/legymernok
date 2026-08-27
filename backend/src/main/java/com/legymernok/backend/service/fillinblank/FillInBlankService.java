package com.legymernok.backend.service.fillinblank;

import com.legymernok.backend.dto.fillinblank.*;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.fillinblank.*;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.fillinblank.*;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.service.rag.ContentChunkingService;
import com.legymernok.backend.service.streak.StreakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FillInBlankService {

    private final MissionRepository missionRepository;
    private final FillInBlankDefinitionRepository definitionRepository;
    private final FillInBlankBlankRepository blankRepository;
    private final FillInBlankOptionRepository optionRepository;
    private final FillInBlankAttemptRepository attemptRepository;
    private final FillInBlankAnswerDetailRepository answerDetailRepository;
    private final CadetRepository cadetRepository;
    private final StreakService streakService;
    private final ContentChunkingService contentChunkingService;

    @Transactional
    public FillInBlankUserResponse saveDefinition(UUID missionId, SaveFillInBlankRequest request) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));

        if (mission.getMissionType() != MissionType.FILL_IN_BLANK) {
            throw new IllegalArgumentException("Mission is not FILL_IN_BLANK type");
        }

        // Ha létezik, teljes csere: blank + option törlése
        definitionRepository.findByMissionId(missionId).ifPresent(existing -> {
            // Előbb töröljük az answer detail-eket (FK a blank_id-ra)
            answerDetailRepository.deleteAllByMissionId(missionId);
            List<FillInBlankBlank> blanks = blankRepository.findAllByDefinitionIdOrderByOrderIndexAsc(existing.getId());
            blanks.forEach(b -> {
                optionRepository.findAllByBlankIdOrderByOrderIndexAsc(b.getId())
                        .forEach(optionRepository::delete);
                blankRepository.delete(b);
            });
            definitionRepository.delete(existing);
        });
        definitionRepository.flush();

        FillInBlankDefinition definition = FillInBlankDefinition.builder()
                .mission(mission)
                .templateText(request.getTemplateText())
                .passThreshold(request.getPassThreshold())
                .build();
        definition = definitionRepository.save(definition);

        for (SaveFillInBlankRequest.BlankRequest blankReq : request.getBlanks()) {
            FillInBlankBlank blank = FillInBlankBlank.builder()
                    .definition(definition)
                    .blanksKey(blankReq.getKey())
                    .orderIndex(blankReq.getOrderIndex())
                    .build();
            blank = blankRepository.save(blank);

            for (SaveFillInBlankRequest.OptionRequest optReq : blankReq.getOptions()) {
                FillInBlankOption option = FillInBlankOption.builder()
                        .blank(blank)
                        .optionText(optReq.getOptionText())
                        .correct(optReq.isCorrect())
                        .orderIndex(optReq.getOrderIndex())
                        .build();
                optionRepository.save(option);
            }
        }

        // A templateText EGYETLEN mentési útvonala — a RAG-index innen frissül.
        contentChunkingService.reindexFillInBlankOnly(missionId);

        return getDefinitionForUser(missionId);
    }

    @Transactional(readOnly = true)
    public FillInBlankUserResponse getDefinitionForUser(UUID missionId) {
        FillInBlankDefinition definition = definitionRepository.findByMissionId(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("FillInBlankDefinition", "missionId", missionId));

        List<FillInBlankBlank> blanks = blankRepository.findAllByDefinitionIdOrderByOrderIndexAsc(definition.getId());

        List<FillInBlankUserResponse.BlankResponse> blankResponses = blanks.stream().map(blank -> {
            List<FillInBlankUserResponse.OptionResponse> optionResponses = optionRepository
                    .findAllByBlankIdOrderByOrderIndexAsc(blank.getId()).stream()
                    .map(opt -> FillInBlankUserResponse.OptionResponse.builder()
                            .id(opt.getId())
                            .optionText(opt.getOptionText())
                            .orderIndex(opt.getOrderIndex())
                            // correct mező NINCS — szándékosan
                            .build())
                    .collect(Collectors.toList());
            return FillInBlankUserResponse.BlankResponse.builder()
                    .id(blank.getId())
                    .key(blank.getBlanksKey())
                    .orderIndex(blank.getOrderIndex())
                    .options(optionResponses)
                    .build();
        }).collect(Collectors.toList());

        return FillInBlankUserResponse.builder()
                .missionId(missionId)
                .templateText(definition.getTemplateText())
                .passThreshold(definition.getPassThreshold())
                .blanks(blankResponses)
                .build();
    }

    @Transactional(readOnly = true)
    public SaveFillInBlankRequest getDefinitionForAdmin(UUID missionId) {
        FillInBlankDefinition definition = definitionRepository.findByMissionId(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("FillInBlankDefinition", "missionId", missionId));

        List<FillInBlankBlank> blanks = blankRepository.findAllByDefinitionIdOrderByOrderIndexAsc(definition.getId());

        List<SaveFillInBlankRequest.BlankRequest> blankRequests = blanks.stream().map(blank -> {
            SaveFillInBlankRequest.BlankRequest blankReq = new SaveFillInBlankRequest.BlankRequest();
            blankReq.setKey(blank.getBlanksKey());
            blankReq.setOrderIndex(blank.getOrderIndex());

            List<SaveFillInBlankRequest.OptionRequest> optionRequests = optionRepository
                    .findAllByBlankIdOrderByOrderIndexAsc(blank.getId()).stream()
                    .map(opt -> {
                        SaveFillInBlankRequest.OptionRequest optReq = new SaveFillInBlankRequest.OptionRequest();
                        optReq.setOptionText(opt.getOptionText());
                        optReq.setCorrect(opt.isCorrect());
                        optReq.setOrderIndex(opt.getOrderIndex());
                        return optReq;
                    })
                    .collect(Collectors.toList());
            blankReq.setOptions(optionRequests);
            return blankReq;
        }).collect(Collectors.toList());

        SaveFillInBlankRequest result = new SaveFillInBlankRequest();
        result.setTemplateText(definition.getTemplateText());
        result.setPassThreshold(definition.getPassThreshold());
        result.setBlanks(blankRequests);
        return result;
    }

    @Transactional
    public FillInBlankResultResponse submit(UUID missionId, SubmitFillInBlankRequest request) {
        Cadet cadet = getCurrentAuthenticatedUser();
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));

        FillInBlankDefinition definition = definitionRepository.findByMissionId(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("FillInBlankDefinition", "missionId", missionId));

        List<FillInBlankBlank> blanks = blankRepository.findAllByDefinitionIdOrderByOrderIndexAsc(definition.getId());

        // Összes option betöltése bulk lekérdezéssel
        List<FillInBlankOption> allOptions = optionRepository.findAllByDefinitionId(definition.getId());
        Map<UUID, FillInBlankOption> optionMap = allOptions.stream()
                .collect(Collectors.toMap(FillInBlankOption::getId, o -> o));

        // Blank → opciók listája (helyes opciók kiszűréséhez)
        Map<UUID, List<FillInBlankOption>> blankOptionsMap = allOptions.stream()
                .collect(Collectors.groupingBy(o -> o.getBlank().getId()));

        Map<String, UUID> answers = request.getAnswers() != null ? request.getAnswers() : Collections.emptyMap();

        int score = 0;
        List<FillInBlankResultResponse.BlankResultDetail> details = new ArrayList<>();
        List<FillInBlankAnswerDetail> answerDetails = new ArrayList<>();

        FillInBlankAttempt attempt = FillInBlankAttempt.builder()
                .cadet(cadet)
                .mission(mission)
                .score(0)
                .maxScore(blanks.size())
                .percentage(0)
                .passed(false)
                .build();
        attempt = attemptRepository.save(attempt);

        for (FillInBlankBlank blank : blanks) {
            UUID selectedOptionId = answers.get(blank.getBlanksKey());
            FillInBlankOption selectedOption = selectedOptionId != null ? optionMap.get(selectedOptionId) : null;
            boolean correct = selectedOption != null && selectedOption.isCorrect();
            if (correct) score++;

            // Helyes opciók szövegei (visszajelzéshez)
            List<String> correctOptionTexts = blankOptionsMap.getOrDefault(blank.getId(), List.of())
                    .stream()
                    .filter(FillInBlankOption::isCorrect)
                    .map(FillInBlankOption::getOptionText)
                    .collect(Collectors.toList());

            details.add(FillInBlankResultResponse.BlankResultDetail.builder()
                    .blankKey(blank.getBlanksKey())
                    .selectedOptionId(selectedOptionId)
                    .correct(correct)
                    .correctOptionTexts(correct ? List.of() : correctOptionTexts)
                    .build());

            FillInBlankAnswerDetail answerDetail = FillInBlankAnswerDetail.builder()
                    .attempt(attempt)
                    .blank(blank)
                    .selectedOption(selectedOption)
                    .correct(correct)
                    .build();
            answerDetails.add(answerDetail);
        }

        answerDetailRepository.saveAll(answerDetails);

        int percentage = blanks.isEmpty() ? 100 : (int) Math.round(score * 100.0 / blanks.size());
        boolean passed = definition.getPassThreshold() == null || percentage >= definition.getPassThreshold();

        attempt.setScore(score);
        attempt.setPercentage(percentage);
        attempt.setPassed(passed);
        attempt = attemptRepository.save(attempt);

        if (passed) {
            streakService.recordActivity(cadet.getId());
        }

        return FillInBlankResultResponse.builder()
                .score(score)
                .maxScore(blanks.size())
                .percentage(percentage)
                .passed(passed)
                .submittedAt(attempt.getSubmittedAt())
                .details(details)
                .build();
    }

    @Transactional(readOnly = true)
    public Optional<LastAttemptResponse> getLastAttempt(UUID missionId) {
        Cadet cadet = getCurrentAuthenticatedUser();
        return attemptRepository
                .findTopByCadetIdAndMissionIdOrderBySubmittedAtDesc(cadet.getId(), missionId)
                .map(a -> LastAttemptResponse.builder()
                        .passed(a.isPassed())
                        .percentage(a.getPercentage())
                        .submittedAt(a.getSubmittedAt())
                        .build());
    }

    private Cadet getCurrentAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }
}
