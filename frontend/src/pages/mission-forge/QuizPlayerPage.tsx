import React, { useState } from "react";
import { Box, CircularProgress, Typography } from "@mui/material";
import { useParams, useNavigate } from "react-router-dom";
import { useQuery, useMutation } from "@tanstack/react-query";
import { quizApi } from "../../api/client";
import QuizPlayer from "../../components/forge/quiz/QuizPlayer";
import { RetroPanel } from "../../components/forge/RetroPanel";
import RetroButton from "../../components/RetroButton";

const QuizPlayerPage: React.FC = () => {
  const { missionId } = useParams<{ missionId: string }>();
  const navigate = useNavigate();
  const [result, setResult] = useState<any>(null);

  // 1. Kvíz betöltése
  const {
    data: quiz,
    isLoading,
    error,
  } = useQuery({
    queryKey: ["quiz", missionId],
    queryFn: () => quizApi.startQuiz(missionId!),
    enabled: !!missionId,
    retry: false,
  });

  // 2. Beküldés mutáció
  const submitMutation = useMutation({
    mutationFn: (answers: Record<string, string[]>) =>
      quizApi.submitQuiz(missionId!, answers),
    onSuccess: (data) => {
      setResult(data);
    },
    onError: (err: any) => {
      // Itt kezeljük a 409-et, ha már beküldte
      if (err.response?.status === 409) {
        setResult(err.response.data.data);
      }
    },
  });

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", mt: 10 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error || !quiz) {
    return (
      <Box sx={{ p: 4, textAlign: "center" }}>
        <Typography color="error">ERROR_LOADING_MISSION_DATA</Typography>
        <RetroButton
          color="red"
          labelKey="starMap.back"
          onClick={() => navigate(-1)}
        />
      </Box>
    );
  }

  // Eredmény kijelző nézet
  if (result) {
    return (
      <Box
        sx={{
          width: "100vw",
          height: "100vh",
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          bgcolor: "#121212",
        }}
      >
        <RetroPanel
          title="MISSION_REPORT"
          sx={{ width: "500px", textAlign: "center" }}
        >
          <Typography
            variant="h4"
            sx={{ color: "#32cd32", mb: 2, fontFamily: "monospace" }}
          >
            MISSION_ACCOMPLISHED
          </Typography>
          <Typography sx={{ color: "#aaa", mb: 4, fontFamily: "monospace" }}>
            FINAL_SCORE: {result.score} / {result.maxScore}
          </Typography>
          <Box
            sx={{
              p: 3,
              border: "2px solid #333",
              bgcolor: "rgba(0,0,0,0.5)",
              mb: 4,
            }}
          >
            <Typography
              variant="h2"
              sx={{ color: "#ffb000", fontFamily: "monospace" }}
            >
              {Math.round(result.percentage)}%
            </Typography>
          </Box>
          <RetroButton
            color="blue"
            labelKey="starMap.back"
            onClick={() => navigate(-1)}
          />
        </RetroPanel>
      </Box>
    );
  }

  return (
    <Box sx={{ width: "100vw", height: "100vh", bgcolor: "#000" }}>
      <QuizPlayer
        data={quiz}
        onSubmit={(answers) => submitMutation.mutate(answers)}
      />
    </Box>
  );
};

export default QuizPlayerPage;
