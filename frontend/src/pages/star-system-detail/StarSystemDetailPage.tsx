import React, { useEffect, useState } from "react";
import { Box, Typography, Paper, Chip } from "@mui/material";
import { useParams, useNavigate } from "react-router-dom";
import { starSystemApi, groupProgressApi } from "../../api/client";
import type {
  StarSystemWithItemsResponse,
  StarSystemItemResponse,
} from "../../types/starSystem";
import type {
  GroupProgressResponse,
  GroupDisplayProgress,
} from "../../types/group";
import type { MissionResponse } from "../../types/mission";
import RetroButton from "../../components/RetroButton";
import "../../styles/RetroUI.css";

const getDifficultyColor = (diff: string) => {
  if (diff === "EASY") return "#0f0";
  if (diff === "MEDIUM") return "#ff0";
  if (diff === "HARD") return "#f00";
  return "#fff";
};

const getMissionRoute = (mission: MissionResponse): string => {
  switch (mission.missionType) {
    case "QUIZ":
      return `/play/quiz/${mission.id}`;
    case "CONTENT":
      return `/play/content/${mission.id}`;
    default:
      return `/play/quiz/${mission.id}`;
  }
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

const GroupCard: React.FC<GroupCardProps> = ({
  item,
  progress,
  onStart,
  onContinue,
  onReplay,
}) => {
  const group = item.group!;
  const missions = item.groupMissions ?? [];

  const statusLabel = (() => {
    if (!progress || progress.status === "NOT_STARTED") return null;
    if (progress.status === "COMPLETED")
      return (
        <Chip
          label="✓ KÉSZ"
          size="small"
          sx={{ bgcolor: "#0a3d0a", color: "#0f0", fontFamily: "monospace" }}
        />
      );
    return (
      <Chip
        label={`${progress.completedCount} / ${progress.totalCount}`}
        size="small"
        sx={{ bgcolor: "#3d2d0a", color: "#ffb000", fontFamily: "monospace" }}
      />
    );
  })();

  return (
    <Paper
      sx={{
        p: 2,
        mb: 2,
        bgcolor: "#111",
        border: "1px solid #333",
        color: "#ccc",
        "&:hover": { borderColor: "#0f0", boxShadow: "0 0 8px rgba(0,255,0,0.1)" },
      }}
    >
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", mb: 1 }}>
        <Box>
          <Typography sx={{ color: "#ffb000", fontFamily: "monospace", fontWeight: "bold" }}>
            [{group.name.toUpperCase()}]
          </Typography>
          <Typography variant="caption" sx={{ color: "#555" }}>
            {missions.length} lépés
          </Typography>
        </Box>
        {statusLabel}
      </Box>

      <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}>
        {(!progress || progress.status === "NOT_STARTED") && (
          <RetroButton color="blue" labelKey="play.start" onClick={onStart} />
        )}
        {progress?.status === "IN_PROGRESS" && (
          <RetroButton color="yellow" labelKey="play.continue" onClick={onContinue} />
        )}
        {progress?.status === "COMPLETED" && (
          <RetroButton color="green" labelKey="play.replay" onClick={onReplay} />
        )}
      </Box>
    </Paper>
  );
};

// ─────────────────────────────────────────────
// StarSystemDetailPage
// ─────────────────────────────────────────────
const StarSystemDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [data, setData] = useState<StarSystemWithItemsResponse | null>(null);
  const [groupProgress, setGroupProgress] = useState<
    Map<string, GroupDisplayProgress>
  >(new Map());
  const [loading, setLoading] = useState(true);

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
              let status: GroupDisplayProgress["status"] = prog.completed
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

  if (loading) return <div className="loading-screen">LOADING...</div>;
  if (!data) return <div className="error-screen">SYSTEM NOT FOUND</div>;

  return (
    <Box
      sx={{
        width: "100vw",
        minHeight: "100vh",
        bgcolor: "#1a1a1a",
        p: 2,
        display: "flex",
        justifyContent: "center",
      }}
    >
      <div
        className="control-panel-casing"
        style={{ width: "100%", maxWidth: "1200px", display: "flex", flexDirection: "column" }}
      >
        <div className="screw top-left" />
        <div className="screw top-right" />
        <div className="screw bottom-left" />
        <div className="screw bottom-right" />

        <Box
          sx={{
            display: "flex",
            justifyContent: "space-between",
            mb: 2,
            borderBottom: "2px solid #333",
            pb: 1,
          }}
        >
          <Typography variant="h5" className="retro-font-header">
            SYSTEM_DATA: {data.name.toUpperCase()}
          </Typography>
        </Box>

        <Box sx={{ display: "flex", gap: 2, flex: 1 }}>
          {/* Bal panel */}
          <Box sx={{ width: 260, flexShrink: 0 }}>
            <div className="crt-monitor">
              <div className="screen-overlay" />
              <div className="terminal-content" style={{ padding: "20px" }}>
                <Typography variant="h5" sx={{ color: "#0f0", textShadow: "0 0 10px #0f0", mb: 1 }}>
                  {data.name}
                </Typography>
                <Typography variant="body2" sx={{ fontFamily: "monospace", lineHeight: 1.5, color: "#aaa" }}>
                  {data.description || "No description available."}
                </Typography>
                <Box sx={{ mt: 3 }}>
                  <Typography variant="caption" sx={{ color: "#555" }}>
                    SECTOR ID: {data.id.split("-")[0]}
                  </Typography>
                  <br />
                  <Typography variant="caption" sx={{ color: "#555" }}>
                    ITEMS: {data.items.length}
                  </Typography>
                </Box>
              </div>
            </div>

            <Box sx={{ mt: 2, display: "flex", justifyContent: "space-around" }}>
              <div className="button-group">
                <button className="retro-btn red" onClick={() => navigate(-1)} />
                <div className="label-plate">BACK</div>
              </div>
            </Box>
          </Box>

          {/* Jobb panel — items lista */}
          <Box
            sx={{
              flex: 1,
              bgcolor: "#000",
              border: "2px solid #333",
              borderRadius: "5px",
              p: 2,
              overflowY: "auto",
              fontFamily: '"VT323", monospace',
            }}
          >
            <Typography variant="h5" sx={{ color: "#fff", mb: 2, borderBottom: "1px dashed #555", pb: 1 }}>
              AVAILABLE CONTENT
            </Typography>

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
                <Paper
                  key={mission.id}
                  sx={{
                    p: 2,
                    mb: 2,
                    bgcolor: "#111",
                    border: "1px solid #333",
                    color: "#0f0",
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    "&:hover": { borderColor: "#0f0", boxShadow: "0 0 10px rgba(0,255,0,0.2)" },
                  }}
                >
                  <Box>
                    <Typography variant="h6" sx={{ fontFamily: "monospace" }}>
                      {mission.name}
                    </Typography>
                    <Typography variant="caption" sx={{ color: "#888" }}>
                      DIFFICULTY:{" "}
                      <span style={{ color: getDifficultyColor(mission.difficulty) }}>
                        {mission.difficulty}
                      </span>
                      {" · "}
                      <span style={{ color: "#888" }}>{mission.missionType.replace(/_/g, " ")}</span>
                    </Typography>
                  </Box>
                  <RetroButton
                    color="green"
                    labelKey="play.start"
                    onClick={() => navigate(getMissionRoute(mission))}
                  />
                </Paper>
              );
            })}

            {data.items.length === 0 && (
              <Typography sx={{ color: "#666", textAlign: "center", mt: 4 }}>
                NO CONTENT AVAILABLE IN THIS SYSTEM.
              </Typography>
            )}
          </Box>
        </Box>
      </div>
    </Box>
  );
};

export default StarSystemDetailPage;
