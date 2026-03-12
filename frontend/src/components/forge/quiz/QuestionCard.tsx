import React from "react";
import {
  Box,
  Typography,
  TextField,
  IconButton,
  Grid,
  Tooltip,
} from "@mui/material";
import { Trash2, Plus, GripVertical } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { QuizQuestion, QuizOption } from "../../../types/quiz";
import OptionRow from "./OptionRow";
import RetroButton from "../../RetroButton";

interface QuestionCardProps {
  question: QuizQuestion;
  index: number;
  onChange: (updated: QuizQuestion) => void;
  onDelete: () => void;
}

const QuestionCard: React.FC<QuestionCardProps> = ({
  question,
  index,
  onChange,
  onDelete,
}) => {
  const { t } = useTranslation();

  const handleOptionChange = (optionId: string, updated: QuizOption) => {
    const newOptions = question.options.map((o) =>
      o.id === optionId ? updated : o
    );
    onChange({ ...question, options: newOptions });
  };

  const addOption = () => {
    if (question.options.length >= 5) return;
    const newOption: QuizOption = {
      id: `o_${crypto.randomUUID()}`,
      text: "",
      isCorrect: false,
    };
    onChange({ ...question, options: [...question.options, newOption] });
  };

  const deleteOption = (id: string) => {
    onChange({
      ...question,
      options: question.options.filter((o) => o.id !== id),
    });
  };

  const correctCount = question.options.filter((o) => o.isCorrect).length;

  return (
    <Box
      sx={{
        mb: 4,
        border: "1px solid #444",
        bgcolor: "#111",
        position: "relative",
        "&:hover": { borderColor: "#ffb000" },
      }}
    >
      {/* HEADER / WINDOW BAR */}
      <Box
        sx={{
          bgcolor: "#222",
          p: 1,
          px: 2,
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          borderBottom: "1px solid #444",
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
          <GripVertical size={16} color="#444" />
          <Typography
            variant="caption"
            sx={{ color: "#ffb000", fontFamily: "monospace", fontWeight: "bold" }}
          >
            [QUESTION_{String(index + 1).padStart(2, "0")}]
          </Typography>
          <Typography
            variant="caption"
            sx={{
              color: correctCount > 1 ? "#32cd32" : "#888",
              fontFamily: "monospace",
              fontSize: "0.7rem",
            }}
          >
            {correctCount > 1 ? "MULTI_SELECTION_MODE" : "SINGLE_SELECTION_MODE"}
          </Typography>
        </Box>
        <IconButton
          size="small"
          onClick={onDelete}
          sx={{ color: "#666", "&:hover": { color: "#ff4444" } }}
        >
          <Trash2 size={16} />
        </IconButton>
      </Box>

      {/* CONTENT */}
      <Box sx={{ p: 3 }}>
        <Grid container spacing={3}>
          <Grid size={9}>
            <TextField
              fullWidth
              multiline
              rows={2}
              label={t("quiz.questionText").toUpperCase()}
              variant="filled"
              value={question.text}
              onChange={(e) => onChange({ ...question, text: e.target.value })}
              sx={{
                "& .MuiInputBase-root": { color: "#fff", fontFamily: "monospace" },
                "& .MuiInputLabel-root": { color: "#888", fontFamily: "monospace" },
                bgcolor: "#050505",
              }}
            />
          </Grid>
          <Grid size={3}>
            <TextField
              fullWidth
              type="number"
              label={t("quiz.points").toUpperCase()}
              variant="filled"
              value={question.points}
              onChange={(e) =>
                onChange({ ...question, points: parseInt(e.target.value) || 0 })
              }
              sx={{
                "& .MuiInputBase-root": {
                  color: "#ffb000",
                  fontFamily: "monospace",
                  textAlign: "center",
                },
                "& .MuiInputLabel-root": { color: "#888", fontFamily: "monospace" },
                bgcolor: "#050505",
              }}
            />
          </Grid>

          <Grid size={12}>
            <Typography
              variant="caption"
              sx={{ color: "#555", mb: 2, display: "block" }}
            >
              {t("quiz.options").toUpperCase()} ({question.options.length}/5)
            </Typography>

            {question.options.map((opt) => (
              <OptionRow
                key={opt.id}
                option={opt}
                isMulti={correctCount > 1}
                onChange={(updated) => handleOptionChange(opt.id, updated)}
                onDelete={() => deleteOption(opt.id)}
              />
            ))}

            {question.options.length < 5 && (
              <Box
                onClick={addOption}
                sx={{
                  mt: 2,
                  p: 1.5,
                  border: "1px dashed #333",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: 1,
                  cursor: "pointer",
                  color: "#555",
                  "&:hover": { color: "#aaa", borderColor: "#aaa" },
                  fontFamily: "monospace",
                  fontSize: "0.8rem",
                }}
              >
                <Plus size={14} />
                {t("quiz.addOption").toUpperCase()}
              </Box>
            )}
          </Grid>
        </Grid>
      </Box>
    </Box>
  );
};

export default QuestionCard;
