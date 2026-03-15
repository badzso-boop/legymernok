import React from "react";
import {
  Box,
  Typography,
  Radio,
  RadioGroup,
  Checkbox,
  FormControlLabel,
  FormGroup,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import type { QuizQuestion } from "../../../types/quiz";

interface QuestionCardViewProps {
  question: QuizQuestion;
  selectedOptions: string[];
  onOptionToggle: (optionId: string) => void;
  showResults?: boolean; // Ha a végén vagyunk és látni kell a jót
}

const QuestionCardView: React.FC<QuestionCardViewProps> = ({
  question,
  selectedOptions,
  onOptionToggle,
}) => {
  const { t } = useTranslation();
  // Production módban a backend küldi az isMulti-t; preview módban isCorrect alapján döntjük el
  const isMulti = question.isMulti ??
    question.options.filter((o) => o.isCorrect).length > 1;

  return (
    <Box
      sx={{
        width: "100%",
        maxWidth: "700px",
        bgcolor: "#0a0a0a",
        border: "2px solid #333",
        p: 4,
        position: "relative",
        boxShadow: "0 0 20px rgba(0,0,0,0.5)",
      }}
    >
      {/* Kérdés Szövege */}
      <Typography
        variant="h5"
        sx={{
          color: "#fff",
          fontFamily: "monospace",
          mb: 4,
          lineHeight: 1.4,
          borderLeft: "4px solid #ffb000",
          pl: 2,
        }}
      >
        {question.text}
      </Typography>

      {/* Válaszlehetőségek */}
      <Box sx={{ mt: 2 }}>
        {isMulti ? (
          <FormGroup>
            {question.options.map((opt) => (
              <FormControlLabel
                key={opt.id}
                control={
                  <Checkbox
                    checked={selectedOptions.includes(opt.id)}
                    onChange={() => onOptionToggle(opt.id)}
                    sx={{
                      color: "#444",
                      "&.Mui-checked": { color: "#ffb000" },
                    }}
                  />
                }
                label={
                  <Typography
                    sx={{
                      fontFamily: "monospace",
                      color: selectedOptions.includes(opt.id)
                        ? "#ffb000"
                        : "#aaa",
                    }}
                  >
                    {opt.text}
                  </Typography>
                }
                sx={{
                  mb: 1,
                  p: 1,
                  border: "1px solid transparent",
                  "&:hover": { bgcolor: "rgba(255,255,255,0.05)" },
                }}
              />
            ))}
          </FormGroup>
        ) : (
          <RadioGroup
            value={selectedOptions[0] || ""}
            onChange={(e) => onOptionToggle(e.target.value)}
          >
            {question.options.map((opt) => (
              <FormControlLabel
                key={opt.id}
                value={opt.id}
                control={
                  <Radio
                    sx={{
                      color: "#444",
                      "&.Mui-checked": { color: "#ffb000" },
                    }}
                  />
                }
                label={
                  <Typography
                    sx={{
                      fontFamily: "monospace",
                      color: selectedOptions.includes(opt.id)
                        ? "#ffb000"
                        : "#aaa",
                    }}
                  >
                    {opt.text}
                  </Typography>
                }
                sx={{
                  mb: 1,
                  p: 1,
                  "&:hover": { bgcolor: "rgba(255,255,255,0.05)" },
                }}
              />
            ))}
          </RadioGroup>
        )}
      </Box>

      {/* Pontszám jelzés */}
      <Box
        sx={{
          position: "absolute",
          top: -12,
          right: 20,
          bgcolor: "#222",
          px: 1,
          border: "1px solid #444",
        }}
      >
        <Typography
          variant="caption"
          sx={{ color: "#ffb000", fontFamily: "monospace", fontSize: "0.7rem" }}
        >
          VALUE: {question.points} PTS
        </Typography>
      </Box>

      {/* Single / Multi jelzés */}
      <Box
        sx={{
          position: "absolute",
          top: -12,
          left: 20,
          bgcolor: "#222",
          px: 1,
          border: `1px solid ${isMulti ? "#32cd32" : "#555"}`,
        }}
      >
        <Typography
          variant="caption"
          sx={{
            color: isMulti ? "#32cd32" : "#666",
            fontFamily: "monospace",
            fontSize: "0.7rem",
          }}
        >
          {isMulti ? t("quizEditor.multiSelection") : t("quizEditor.singleSelection")}
        </Typography>
      </Box>
    </Box>
  );
};

export default QuestionCardView;
