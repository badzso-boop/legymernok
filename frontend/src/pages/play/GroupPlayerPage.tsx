import React, { useEffect, useState } from "react";
import { Box, Typography, LinearProgress } from "@mui/material";
import { useParams, useNavigate } from "react-router-dom";
import { missionGroupApi, groupProgressApi } from "../../api/client";
import type { MissionGroupWithMissionsResponse } from "../../types/group";
import type { GroupProgressResponse } from "../../types/group";
import type { MissionResult } from "../../types/quiz";
import ContentMissionView from "../../components/play/ContentMissionView";
import FillInBlankView from "../../components/play/FillInBlankView";
import QuizPlayerComponent from "../../components/forge/quiz/QuizPlayerComponent";
import RetroButton from "../../components/RetroButton";
import "../../styles/RetroUI.css";

const GroupPlayerPage: React.FC = () => {
  const { groupId } = useParams<{ groupId: string }>();
  const navigate = useNavigate();

  const [groupData, setGroupData] = useState<MissionGroupWithMissionsResponse | null>(null);
  const [progress, setProgress] = useState<GroupProgressResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [completing, setCompleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!groupId) return;

    const loadGroup = async () => {
      try {
        const group = await missionGroupApi.getById(groupId);
        setGroupData(group);
        await loadProgress(groupId);
      } catch {
        setError("A csoport nem érhető el.");
      } finally {
        setLoading(false);
      }
    };

    loadGroup();
  }, [groupId]);

  const loadProgress = async (gid: string) => {
    try {
      const prog = await groupProgressApi.get(gid);
      setProgress(prog);
    } catch (e: any) {
      if (e?.response?.status === 404) {
        // Első belépés — indítjuk a csoportot
        try {
          await groupProgressApi.start(gid);
          const prog = await groupProgressApi.get(gid);
          setProgress(prog);
        } catch (startErr: any) {
          if (startErr?.response?.status === 409) {
            // Versenyhelyzet — GET újra
            const prog = await groupProgressApi.get(gid);
            setProgress(prog);
          } else {
            throw startErr;
          }
        }
      } else {
        throw e;
      }
    }
  };

  const handleCompleteStep = async () => {
    if (!groupId) return;
    setCompleting(true);
    try {
      const updated = await groupProgressApi.completeStep(groupId);
      setProgress(updated);
    } finally {
      setCompleting(false);
    }
  };

  // QuizPlayerComponent onComplete signature is (result: MissionResult) → wrap to void
  const handleQuizComplete = (_result: MissionResult) => {
    handleCompleteStep();
  };

  // ── Loading ──────────────────────────────────────────────────
  if (loading) {
    return <div className="loading-screen">LOADING...</div>;
  }

  if (error || !groupData) {
    return <div className="error-screen">{error ?? "GROUP NOT FOUND"}</div>;
  }

  // ── Befejezési képernyő ──────────────────────────────────────
  if (progress?.completed) {
    return (
      <Box
        sx={{
          width: "100vw",
          minHeight: "100vh",
          bgcolor: "#1a1a1a",
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          p: 2,
        }}
      >
        <div
          className="control-panel-casing"
          style={{
            width: "100%",
            maxWidth: 600,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 24,
            padding: 40,
          }}
        >
          <div className="screw top-left" />
          <div className="screw top-right" />
          <div className="screw bottom-left" />
          <div className="screw bottom-right" />

          <Typography
            sx={{
              fontFamily: '"VT323", monospace',
              fontSize: "2rem",
              color: "#0f0",
              textShadow: "0 0 12px #0f0",
              letterSpacing: 3,
              textAlign: "center",
            }}
          >
            ✓ CSOPORT TELJESÍTVE
          </Typography>

          <Typography
            sx={{
              fontFamily: '"VT323", monospace',
              fontSize: "1.3rem",
              color: "#ffb000",
              textAlign: "center",
            }}
          >
            [{groupData.name.toUpperCase()}]
          </Typography>

          <Box
            sx={{
              width: "100%",
              bgcolor: "#0a2a0a",
              border: "1px solid #0f0",
              borderRadius: 1,
              p: 2,
              textAlign: "center",
            }}
          >
            <Typography sx={{ fontFamily: "monospace", color: "#0f0", fontSize: "0.9rem" }}>
              MISSZIÓ_LÉPÉSEK: {progress.totalCount} / {progress.totalCount}
            </Typography>
          </Box>

          <RetroButton
            color="green"
            labelKey="play.backToSystem"
            onClick={() => navigate(`/star-systems/${groupData.starSystemId}`)}
          />
        </div>
      </Box>
    );
  }

  // ── Aktuális misszió meghatározása ───────────────────────────
  const currentMission =
    groupData.missions.find((m) => m.id === progress?.nextMissionId) ?? null;

  // ── Misszió tartalom ─────────────────────────────────────────
  const renderMission = () => {
    if (!currentMission) {
      return (
        <Typography sx={{ color: "#555", fontFamily: "monospace" }}>
          Nincs következő misszió.
        </Typography>
      );
    }

    switch (currentMission.missionType) {
      case "CONTENT":
        return (
          <ContentMissionView
            missionId={currentMission.id}
            onComplete={handleCompleteStep}
          />
        );
      case "FILL_IN_BLANK":
        return (
          <FillInBlankView
            missionId={currentMission.id}
            onComplete={handleCompleteStep}
          />
        );
      case "QUIZ":
        return (
          <QuizPlayerComponent
            missionId={currentMission.id}
            onComplete={handleQuizComplete}
          />
        );
      default:
        return (
          <Box
            sx={{
              p: 3,
              border: "1px dashed #333",
              borderRadius: 1,
              textAlign: "center",
            }}
          >
            <Typography sx={{ color: "#555", fontFamily: "monospace" }}>
              {currentMission.missionType} — Hamarosan elérhető
            </Typography>
          </Box>
        );
    }
  };

  // ── Fő layout ────────────────────────────────────────────────
  const completedCount = progress?.completedCount ?? 0;
  const totalCount = progress?.totalCount ?? groupData.missions.length;
  const progressPct = totalCount > 0 ? (completedCount / totalCount) * 100 : 0;

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
        style={{ width: "100%", maxWidth: 900, display: "flex", flexDirection: "column" }}
      >
        <div className="screw top-left" />
        <div className="screw top-right" />
        <div className="screw bottom-left" />
        <div className="screw bottom-right" />

        {/* Fejléc */}
        <Box
          sx={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            mb: 2,
            borderBottom: "2px solid #333",
            pb: 1,
            gap: 2,
            flexWrap: "wrap",
          }}
        >
          <Box>
            <Typography
              sx={{
                fontFamily: '"VT323", monospace',
                fontSize: "1.3rem",
                color: "#ffb000",
                letterSpacing: 2,
              }}
            >
              [{groupData.name.toUpperCase()}]
            </Typography>
            {currentMission && (
              <Typography
                sx={{ fontFamily: "monospace", fontSize: "0.8rem", color: "#555" }}
              >
                {currentMission.name}
              </Typography>
            )}
          </Box>

          <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
            <Typography
              sx={{
                fontFamily: '"VT323", monospace',
                fontSize: "1.1rem",
                color: "#0f0",
                letterSpacing: 1,
              }}
            >
              {completedCount + 1} / {totalCount}
            </Typography>
            <button
              className="retro-btn red"
              onClick={() => navigate(`/star-systems/${groupData.starSystemId}`)}
              title="Vissza"
              style={{ width: 28, height: 28 }}
            />
          </Box>
        </Box>

        {/* Progress bar */}
        <LinearProgress
          variant="determinate"
          value={progressPct}
          sx={{
            mb: 2,
            height: 4,
            bgcolor: "#111",
            "& .MuiLinearProgress-bar": { bgcolor: "#0f0" },
          }}
        />

        {/* Completing overlay */}
        {completing && (
          <Box sx={{ mb: 2 }}>
            <LinearProgress
              sx={{
                height: 2,
                bgcolor: "#111",
                "& .MuiLinearProgress-bar": { bgcolor: "#ffb000" },
              }}
            />
          </Box>
        )}

        {/* Misszió tartalom */}
        <Box sx={{ flex: 1, overflow: "auto" }}>{renderMission()}</Box>
      </div>
    </Box>
  );
};

export default GroupPlayerPage;
