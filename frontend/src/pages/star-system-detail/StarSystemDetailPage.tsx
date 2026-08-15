import React, { useEffect, useState } from "react";
import { Box, Typography, Chip, CircularProgress, Alert } from "@mui/material";
import {
  Code as CodeIcon,
  Quiz as QuizIcon,
  Description as DescriptionIcon,
  Extension as ExtensionIcon,
  ElectricBolt as ElectricBoltIcon,
  Rocket as RocketIcon,
} from "@mui/icons-material";
import { useParams, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { starSystemApi, groupProgressApi, missionApi } from "../../api/client";
import type {
  StarSystemWithItemsResponse,
  StarSystemItemResponse,
} from "../../types/starSystem";
import type {
  GroupProgressResponse,
  GroupDisplayProgress,
} from "../../types/group";
import type { MissionResponse } from "../../types/mission";
import { StarfieldBackground } from "../../components/shared/StarfieldBackground";
import { NebulaLayer } from "../../components/shared/NebulaLayer";
import { GlowCard } from "../../components/shared/GlowCard";
import { NeonButton } from "../../components/shared/NeonButton";

const TYPE_ICONS: Record<string, React.ReactElement> = {
  CODING: <CodeIcon fontSize="small" sx={{ color: "var(--color-text-secondary)" }} />,
  QUIZ: <QuizIcon fontSize="small" sx={{ color: "var(--color-text-secondary)" }} />,
  CONTENT: <DescriptionIcon fontSize="small" sx={{ color: "var(--color-text-secondary)" }} />,
  FILL_IN_BLANK: <ExtensionIcon fontSize="small" sx={{ color: "var(--color-text-secondary)" }} />,
  CIRCUIT_SIMULATION: <ElectricBoltIcon fontSize="small" sx={{ color: "var(--color-text-secondary)" }} />,
};

const MissionTypeIcon: React.FC<{ type: string }> = ({ type }) =>
  TYPE_ICONS[type] ?? <RocketIcon fontSize="small" sx={{ color: "var(--color-text-secondary)" }} />;

const DIFFICULTY_COLOR: Record<string, string> = {
  EASY: "var(--color-success)",
  MEDIUM: "var(--color-accent-secondary)",
  HARD: "#f87171",
  EXPERT: "#f87171",
};

// ─────────────────────────────────────────────
// GroupCard
// ─────────────────────────────────────────────
interface GroupCardProps {
  item: StarSystemItemResponse;
  progress: GroupDisplayProgress | null;
  onStart: () => void;
  onContinue: () => void;
  onReplay: () => void;
}

const GroupCard: React.FC<GroupCardProps> = ({ item, progress, onStart, onContinue, onReplay }) => {
  const { t } = useTranslation();
  const group = item.group!;
  const missions = item.groupMissions ?? [];

  const statusChip = (() => {
    if (!progress || progress.status === "NOT_STARTED") return null;
    if (progress.status === "COMPLETED")
      return (
        <Chip
          label={`✓ ${t("starMap.statusCompleted")}`}
          size="small"
          sx={{ bgcolor: "var(--color-bg-elevated)", color: "var(--color-success)" }}
        />
      );
    return (
      <Chip
        label={`${progress.completedCount} / ${progress.totalCount}`}
        size="small"
        sx={{ bgcolor: "var(--color-bg-elevated)", color: "var(--color-accent-primary)" }}
      />
    );
  })();

  return (
    <GlowCard active={progress?.status === "IN_PROGRESS"} sx={{ mb: 2 }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", mb: 1 }}>
        <Box>
          <Typography sx={{ fontWeight: 700 }}>{group.name}</Typography>
          <Typography variant="caption" sx={{ color: "var(--color-text-secondary)" }}>
            {t("starSystemDetail.stepsCount", { count: missions.length })}
          </Typography>
        </Box>
        {statusChip}
      </Box>

      <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
        {(!progress || progress.status === "NOT_STARTED") && (
          <NeonButton size="small" onClick={onStart}>
            {t("play.start")}
          </NeonButton>
        )}
        {progress?.status === "IN_PROGRESS" && (
          <NeonButton size="small" onClick={onContinue}>
            {t("play.continue")}
          </NeonButton>
        )}
        {progress?.status === "COMPLETED" && (
          <NeonButton size="small" onClick={onReplay}>
            {t("play.replay")}
          </NeonButton>
        )}
      </Box>
    </GlowCard>
  );
};

// ─────────────────────────────────────────────
// StarSystemDetailPage
// ─────────────────────────────────────────────
const StarSystemDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation();

  const [data, setData] = useState<StarSystemWithItemsResponse | null>(null);
  const [groupProgress, setGroupProgress] = useState<Map<string, GroupDisplayProgress>>(new Map());
  const [loading, setLoading] = useState(true);
  const [startingMissionId, setStartingMissionId] = useState<string | null>(null);
  const [startError, setStartError] = useState<string | null>(null);

  const handleMissionStart = async (mission: MissionResponse) => {
    setStartError(null);

    if (mission.missionType === "QUIZ") {
      navigate(`/play/quiz/${mission.id}`, { state: { starSystemId: id } });
      return;
    }
    if (mission.missionType === "CONTENT") {
      navigate(`/play/content/${mission.id}`, { state: { starSystemId: id } });
      return;
    }
    if (mission.missionType === "FILL_IN_BLANK") {
      setStartError(t("starSystemDetail.fillInBlankNotSupported", { name: mission.name }));
      return;
    }
    if (mission.missionType === "CODING") {
      navigate(`/play/coding/${mission.id}`, { state: { starSystemId: id } });
      return;
    }

    // CIRCUIT_SIMULATION — nincs még dedikált kadét-oldali munkakörnyezet.
    setStartingMissionId(mission.id);
    try {
      const repoUrl = await missionApi.start(mission.id);
      window.open(repoUrl, "_blank", "noopener,noreferrer");
    } catch (err) {
      console.error(err);
      setStartError(t("starSystemDetail.startError", { name: mission.name }));
    } finally {
      setStartingMissionId(null);
    }
  };

  useEffect(() => {
    const load = async () => {
      try {
        const systemData = await starSystemApi.getWithItems(id!);
        setData(systemData);

        const groupItems = systemData.items.filter((item) => item.type === "GROUP");
        const progressMap = new Map<string, GroupDisplayProgress>();

        await Promise.all(
          groupItems.map(async (item) => {
            const groupId = item.group!.id;
            try {
              const prog: GroupProgressResponse = await groupProgressApi.get(groupId);
              const status: GroupDisplayProgress["status"] = prog.completed
                ? "COMPLETED"
                : "IN_PROGRESS";
              progressMap.set(groupId, {
                status,
                completedCount: prog.completedCount,
                totalCount: prog.totalCount,
              });
            } catch {
              progressMap.set(groupId, {
                status: "NOT_STARTED",
                completedCount: 0,
                totalCount: item.groupMissions?.length ?? 0,
              });
            }
          }),
        );

        setGroupProgress(progressMap);
      } catch {
        // leave data null — show error below
      } finally {
        setLoading(false);
      }
    };
    if (id) load();
  }, [id]);

  if (loading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!data) {
    return (
      <Alert severity="error">{t("starSystemDetail.notFound")}</Alert>
    );
  }

  return (
    <Box sx={{ position: "relative", minHeight: "100%" }}>
      <StarfieldBackground intensity="ambient" />
      <NebulaLayer intensity="ambient" />

      <Box sx={{ position: "relative", zIndex: 1 }}>
        <Typography variant="h4" sx={{ fontWeight: "bold", mb: 0.5 }}>
          {data.name}
        </Typography>
        {data.description && (
          <Typography sx={{ color: "var(--color-text-secondary)", mb: 3, maxWidth: 640 }}>
            {data.description}
          </Typography>
        )}

        {startError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {startError}
          </Alert>
        )}

        {data.items.map((item) => {
          if (item.type === "GROUP") {
            const groupId = item.group!.id;
            const prog = groupProgress.get(groupId) ?? {
              status: "NOT_STARTED" as const,
              completedCount: 0,
              totalCount: item.groupMissions?.length ?? 0,
            };
            return (
              <GroupCard
                key={groupId}
                item={item}
                progress={prog}
                onStart={() => navigate(`/play/group/${groupId}`)}
                onContinue={() => navigate(`/play/group/${groupId}`)}
                onReplay={() => navigate(`/play/group/${groupId}`)}
              />
            );
          }

          const mission = item.mission!;
          return (
            <GlowCard
              key={mission.id}
              sx={{
                mb: 2,
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                gap: 2,
              }}
            >
              <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, minWidth: 0 }}>
                <MissionTypeIcon type={mission.missionType} />
                <Box sx={{ minWidth: 0 }}>
                  <Typography sx={{ fontWeight: 600 }} noWrap>
                    {mission.name}
                  </Typography>
                  <Typography variant="caption" sx={{ color: "var(--color-text-secondary)" }}>
                    <span style={{ color: DIFFICULTY_COLOR[mission.difficulty] ?? "inherit" }}>
                      {mission.difficulty}
                    </span>
                    {" · "}
                    {mission.missionType.replace(/_/g, " ")}
                  </Typography>
                </Box>
              </Box>
              <NeonButton
                size="small"
                disabled={startingMissionId === mission.id}
                onClick={() => handleMissionStart(mission)}
                sx={{ flexShrink: 0 }}
              >
                {t("play.start")}
              </NeonButton>
            </GlowCard>
          );
        })}

        {data.items.length === 0 && (
          <Typography sx={{ color: "var(--color-text-secondary)", textAlign: "center", mt: 4 }}>
            {t("starSystemDetail.empty")}
          </Typography>
        )}
      </Box>
    </Box>
  );
};

export default StarSystemDetailPage;
