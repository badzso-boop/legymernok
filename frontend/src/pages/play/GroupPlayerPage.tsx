import React, { useState } from "react";
import { Box, Typography, CircularProgress } from "@mui/material";
import { useParams, useNavigate } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { missionGroupApi, groupProgressApi } from "../../api/client";
import type { GroupProgressResponse } from "../../types/group";
import type { MissionResult } from "../../types/quiz";
import ContentMissionView from "../../components/play/ContentMissionView";
import FillInBlankView from "../../components/play/FillInBlankView";
import QuizPlayerComponent from "../../components/forge/quiz/QuizPlayerComponent";
import RetroButton from "../../components/RetroButton";
import {
  MissionPlayerActions,
  MissionPlayerShell,
} from "../../components/shared/MissionPlayerShell";

const GroupPlayerPage: React.FC = () => {
  const { groupId } = useParams<{ groupId: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [viewIndex, setViewIndex] = useState<number | null>(null);

  const {
    data: groupData,
    isLoading: isGroupLoading,
    isError: isGroupError,
  } = useQuery({
    queryKey: ["missionGroup", groupId],
    queryFn: () => missionGroupApi.getById(groupId!),
    enabled: !!groupId,
  });

  // Első betöltéskor, ha még nincs progress rekord (404), elindítjuk a
  // csoportot — versenyhelyzet (409) esetén újra GET-tel olvassuk be.
  const { data: progress, isLoading: isProgressLoading } = useQuery({
    queryKey: ["groupProgress", groupId],
    queryFn: async (): Promise<GroupProgressResponse> => {
      try {
        return await groupProgressApi.get(groupId!);
      } catch (e: any) {
        if (e?.response?.status !== 404) throw e;
        try {
          return await groupProgressApi.start(groupId!);
        } catch (startErr: any) {
          if (startErr?.response?.status === 409) {
            return await groupProgressApi.get(groupId!);
          }
          throw startErr;
        }
      }
    },
    enabled: !!groupId,
    retry: false,
  });

  const completeStepMutation = useMutation({
    mutationFn: () => groupProgressApi.completeStep(groupId!),
    onSuccess: (updated) => {
      queryClient.setQueryData(["groupProgress", groupId], updated);
    },
  });

  const handleCompleteStep = () => completeStepMutation.mutate();

  // QuizPlayerComponent onComplete signature is (result: MissionResult) → wrap to void
  const handleQuizComplete = (_result: MissionResult) => handleCompleteStep();

  const loading = isGroupLoading || isProgressLoading;
  const missions = groupData?.missions ?? [];
  const nextMissionIndex = missions.findIndex((m) => m.id === progress?.nextMissionId);
  const effectiveIndex = viewIndex !== null ? viewIndex : Math.max(nextMissionIndex, 0);
  const isReviewing = progress?.completed || (viewIndex !== null && viewIndex < nextMissionIndex);

  const handleNextReview = () => {
    if (effectiveIndex < missions.length - 1) {
      setViewIndex(effectiveIndex + 1);
    } else {
      setViewIndex(null);
    }
  };

  const handleBackStep = () => setViewIndex(Math.max(0, effectiveIndex - 1));
  const handleBackToSystem = () =>
    groupData && navigate(`/star-systems/${groupData.starSystemId}`);

  // ── Loading / hiba ───────────────────────────────────────────
  if (loading) {
    return (
      <MissionPlayerShell>
        <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
          <CircularProgress sx={{ color: "var(--color-accent-primary)" }} />
        </Box>
      </MissionPlayerShell>
    );
  }

  if (isGroupError || !groupData) {
    return (
      <MissionPlayerShell onBack={() => navigate(-1)}>
        <Typography sx={{ color: "var(--color-error)" }}>
          {t("play.groupLoadError")}
        </Typography>
      </MissionPlayerShell>
    );
  }

  const completedCount = progress?.completedCount ?? 0;
  const totalCount = progress?.totalCount ?? groupData.missions.length;
  const progressPct = totalCount > 0 ? completedCount / totalCount : 0;

  // ── Befejezési képernyő ──────────────────────────────────────
  if (progress?.completed && viewIndex === null) {
    return (
      <MissionPlayerShell
        title={groupData.name}
        onBack={handleBackToSystem}
        progress={`${totalCount} / ${totalCount}`}
        progressValue={1}
      >
        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 2,
            py: 4,
            textAlign: "center",
          }}
        >
          <Typography
            sx={{ fontSize: "1.5rem", fontWeight: 700, color: "var(--color-success)" }}
          >
            ✓ {t("play.groupCompleted")}
          </Typography>
        </Box>
        <MissionPlayerActions>
          <RetroButton color="blue" labelKey="play.review" onClick={() => setViewIndex(0)} />
          <RetroButton
            color="green"
            labelKey="play.backToSystem"
            onClick={handleBackToSystem}
          />
        </MissionPlayerActions>
      </MissionPlayerShell>
    );
  }

  // ── Aktuális misszió meghatározása ───────────────────────────
  const currentMission = missions[effectiveIndex] ?? null;

  const renderMission = () => {
    if (!currentMission) {
      return (
        <Typography sx={{ color: "var(--color-text-secondary)" }}>
          {t("play.noNextMission")}
        </Typography>
      );
    }

    switch (currentMission.missionType) {
      case "CONTENT":
        return (
          <ContentMissionView
            missionId={currentMission.id}
            onComplete={isReviewing ? handleNextReview : handleCompleteStep}
          />
        );
      case "FILL_IN_BLANK":
        return (
          <FillInBlankView
            missionId={currentMission.id}
            onComplete={isReviewing ? handleNextReview : handleCompleteStep}
          />
        );
      case "QUIZ":
        return (
          <QuizPlayerComponent
            missionId={currentMission.id}
            onComplete={isReviewing ? () => handleNextReview() : handleQuizComplete}
          />
        );
      default:
        return (
          <Typography sx={{ color: "var(--color-text-secondary)" }}>
            {currentMission.missionType} — {t("play.notYetAvailable")}
          </Typography>
        );
    }
  };

  return (
    <MissionPlayerShell
      title={groupData.name}
      subtitle={currentMission?.name}
      onBack={effectiveIndex > 0 ? handleBackStep : handleBackToSystem}
      progress={t("play.step", { current: effectiveIndex + 1, total: totalCount })}
      progressValue={progressPct}
    >
      {completeStepMutation.isPending && (
        <Box sx={{ mb: 1 }}>
          <CircularProgress size={16} sx={{ color: "var(--color-warning)" }} />
        </Box>
      )}
      {renderMission()}
    </MissionPlayerShell>
  );
};

export default GroupPlayerPage;
