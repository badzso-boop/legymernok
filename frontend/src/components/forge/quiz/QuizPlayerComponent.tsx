import React, { useState } from "react";
import { Box, CircularProgress, Typography } from "@mui/material";
import { useQuery, useMutation } from "@tanstack/react-query";
import { quizApi } from "../../../api/client";
import QuizPlayer from "./QuizPlayer";
import type { MissionResult } from "../../../types/quiz";

interface QuizPlayerComponentProps {
  missionId: string;
  onComplete: (result: MissionResult) => void;
}

const QuizPlayerComponent: React.FC<QuizPlayerComponentProps> = ({
  missionId,
  onComplete,
}) => {
  const [sessionExpired, setSessionExpired] = useState(false);

  const {
    data: quiz,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ["quiz-embed", missionId],
    queryFn: () => quizApi.startQuiz(missionId),
    retry: false,
  });

  const submitMutation = useMutation({
    mutationFn: (answers: Record<string, string[]>) =>
      quizApi.submitQuiz(missionId, answers),
    onSuccess: (result) => {
      onComplete(result);
    },
    onError: (err: any) => {
      if (err.response?.status === 409) {
        onComplete(err.response.data.data);
      } else if (err.response?.status === 404) {
        setSessionExpired(true);
        refetch();
      }
    },
  });

  if (isLoading || (sessionExpired && isLoading)) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
        <CircularProgress sx={{ color: "#ffb000" }} />
      </Box>
    );
  }

  if (error || !quiz) {
    return (
      <Typography
        sx={{ color: "#ff4444", fontFamily: "monospace", textAlign: "center", py: 4 }}
      >
        ERROR_LOADING_QUIZ_DATA
      </Typography>
    );
  }

  return (
    <Box sx={{ width: "100%", height: "100%" }}>
      <QuizPlayer
        data={quiz}
        onSubmit={(answers) => submitMutation.mutate(answers)}
      />
    </Box>
  );
};

export default QuizPlayerComponent;
