import React, { useState, useEffect } from "react";
import { Box, Typography, LinearProgress } from "@mui/material";
import { useTranslation } from "react-i18next";
import type { QuizDefinition } from "../../../types/quiz";
import QuestionCardView from "./QuestionCardView";
import RetroButton from "../../RetroButton";
import { Clock, ChevronLeft, ChevronRight, Send } from "lucide-react";

interface QuizPlayerProps {
  data: QuizDefinition;
  isPreview?: boolean;
  onClose?: () => void;
  onSubmit?: (answers: Record<string, string[]>) => void;
}

const QuizPlayer: React.FC<QuizPlayerProps> = ({
  data,
  isPreview = false,
  onClose,
  onSubmit,
}) => {
  const { t } = useTranslation();
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<string, string[]>>({});
  const [timeLeft, setTimeLeft] = useState(data.config.timeLimitSeconds);

  // IDŐZÍTŐ LOGIKA
  useEffect(() => {
    if (timeLeft <= 0) return;
    const timer = setInterval(() => {
      setTimeLeft((prev) => prev - 1);
    }, 1000);
    return () => clearInterval(timer);
  }, [timeLeft]);

  const currentQuestion = data.questions[currentIndex];
  const progress = ((currentIndex + 1) / data.questions.length) * 100;

  const handleOptionToggle = (optionId: string) => {
    const qId = currentQuestion.id;
    const currentAnswers = answers[qId] || [];

    // Megszámoljuk a helyes válaszokat a sablonban (ha van)
    const correctCount = currentQuestion.options.filter(o => o.isCorrect).length;
    const isMulti = correctCount > 1;

    if (isMulti) {
      const newAnswers = currentAnswers.includes(optionId)
        ? currentAnswers.filter((id) => id !== optionId)
        : [...currentAnswers, optionId];
      setAnswers({ ...answers, [qId]: newAnswers });
    } else {
      setAnswers({ ...answers, [qId]: [optionId] });
    }
  };

  const formatTime = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
  };

  if (!currentQuestion) {
    return <Typography color="error">NO QUESTIONS DATA AVAILABLE</Typography>;
  }

  return (
    <Box
      sx={{
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "column",
        bgcolor: "#000",
        color: "#fff",
        p: 4,
        position: "relative",
      }}
    >
      {/* HEADER: PROGRESS & TIMER */}
      <Box sx={{ mb: 6 }}>
        <Box
          sx={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            mb: 2,
          }}
        >
          <Typography variant="caption" sx={{ fontFamily: "monospace", color: "#888" }}>
            [MISSION_PROGRESS: {currentIndex + 1} / {data.questions.length}]
          </Typography>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
            <Clock size={16} color={timeLeft < 60 ? "#ff4444" : "#ffb000"} />
            <Typography
              variant="h6"
              sx={{
                fontFamily: "monospace",
                color: timeLeft < 60 ? "#ff4444" : "#ffb000",
                textShadow: timeLeft < 60 ? "0 0 10px #f00" : "none",
              }}
            >
              {formatTime(timeLeft)}
            </Typography>
          </Box>
        </Box>
        <LinearProgress
          variant="determinate"
          value={progress}
          sx={{
            height: 4,
            bgcolor: "#222",
            "& .MuiLinearProgress-bar": { bgcolor: "#32cd32" },
          }}
        />
      </Box>

      {/* MAIN CONTENT: QUESTION CARD */}
      <Box sx={{ flexGrow: 1, display: "flex", justifyContent: "center", alignItems: "center" }}>
        <QuestionCardView
          question={currentQuestion}
          selectedOptions={answers[currentQuestion.id] || []}
          onOptionToggle={handleOptionToggle}
        />
      </Box>

      {/* FOOTER: CONTROLS */}
      <Box
        sx={{
          mt: 6,
          display: "flex",
          justifyContent: "center",
          gap: 10,
          borderTop: "1px solid #222",
          pt: 4,
        }}
      >
        <RetroButton
          color="blue"
          labelKey="PREV"
          onClick={() => setCurrentIndex((prev) => Math.max(0, prev - 1))}
          disabled={currentIndex === 0 || !data.config.allowNavigation}
        />

        {currentIndex < data.questions.length - 1 ? (
          <RetroButton
            color="yellow"
            labelKey="NEXT"
            onClick={() => setCurrentIndex((prev) => prev + 1)}
          />
        ) : (
          <RetroButton
            color="green"
            labelKey="FINISH"
            onClick={() => onSubmit?.(answers)}
          />
        )}

        {isPreview && (
          <RetroButton color="red" labelKey="CLOSE_PREVIEW" onClick={onClose} />
        )}
      </Box>

      {isPreview && (
        <Typography
          variant="caption"
          sx={{
            position: "absolute",
            bottom: 10,
            right: 20,
            color: "#444",
            fontFamily: "monospace",
          }}
        >
          PREVIEW_MODE // NO_DATA_RECORDED
        </Typography>
      )}
    </Box>
  );
};

export default QuizPlayer;
