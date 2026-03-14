import React, { useState, useEffect } from "react";
import { Box, Typography, LinearProgress } from "@mui/material";
import { useTranslation } from "react-i18next";
import type { QuizDefinition } from "../../../types/quiz";
import QuestionCardView from "./QuestionCardView";
import RetroButton from "../../RetroButton";
import { Clock } from "lucide-react";

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

  // AUTO-SUBMIT: ha lejár az idő, automatikusan beküldi az aktuális válaszokat
  useEffect(() => {
    if (timeLeft === 0 && !isPreview && onSubmit) {
      onSubmit(answers);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [timeLeft]);

  const currentQuestion = data.questions[currentIndex];
  const progress = ((currentIndex + 1) / data.questions.length) * 100;
  const isTimeUp = timeLeft <= 0 && !isPreview;

  const handleOptionToggle = (optionId: string) => {
    const qId = currentQuestion.id;
    const currentAnswers = answers[qId] || [];

    // Megszámoljuk a helyes válaszokat a sablonban (ha van - pl. preview-nál)
    // Megjegyzés: Production játék módban ez a logika finomításra szorul (isMultiSelect flag kell a backendről)
    const correctCount = currentQuestion.options.filter(
      (o) => o.isCorrect,
    ).length;
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
    return <Typography color="error">{t("quiz.noQuestionsData")}</Typography>;
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
          <Typography
            variant="caption"
            sx={{ fontFamily: "monospace", color: "#888" }}
          >
            [{t("quizEditor.progress")}: {currentIndex + 1} /{" "}
            {data.questions.length}]
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
      <Box
        sx={{
          flexGrow: 1,
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
        }}
      >
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
          labelKey="quizEditor.prev"
          onClick={() => setCurrentIndex((prev) => Math.max(0, prev - 1))}
          disabled={currentIndex === 0 || !data.config.allowNavigation || isTimeUp}
        />

        {currentIndex < data.questions.length - 1 ? (
          <RetroButton
            color="yellow"
            labelKey="quizEditor.next"
            onClick={() => setCurrentIndex((prev) => prev + 1)}
            disabled={isTimeUp}
          />
        ) : (
          <RetroButton
            color="green"
            labelKey="quizEditor.finish"
            onClick={() => onSubmit?.(answers)}
            disabled={isTimeUp}
          />
        )}

        {isPreview && (
          <RetroButton
            color="red"
            labelKey="quiz.closePreview"
            onClick={onClose}
          />
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
          {t("quizEditor.noDataRecorded")}
        </Typography>
      )}
    </Box>
  );
};

export default QuizPlayer;
