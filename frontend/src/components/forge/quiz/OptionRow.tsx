import React from "react";
import {
  Box,
  TextField,
  Checkbox,
  IconButton,
  Tooltip,
  Typography,
} from "@mui/material";
import { Trash2 } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { QuizOption } from "../../../types/quiz";

interface OptionRowProps {
  option: QuizOption;
  onChange: (updated: QuizOption) => void;
  onDelete: () => void;
  isMulti: boolean;
}

const OptionRow: React.FC<OptionRowProps> = ({
  option,
  onChange,
  onDelete,
  isMulti,
}) => {
  const { t } = useTranslation();

  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        gap: 2,
        mb: 1.5,
        p: 1.5,
        bgcolor: "rgba(255,255,255,0.03)",
        border: option.isCorrect ? "1px solid #32cd32" : "1px solid #222",
        transition: "all 0.2s ease",
      }}
    >
      <Tooltip title={t("quizEditor.isCorrect").toUpperCase()}>
        <Checkbox
          checked={option.isCorrect}
          onChange={(e) => onChange({ ...option, isCorrect: e.target.checked })}
          sx={{
            color: "#444",
            "&.Mui-checked": {
              color: "#32cd32",
              textShadow: "0 0 10px #32cd32",
            },
          }}
        />
      </Tooltip>

      <TextField
        fullWidth
        size="small"
        placeholder={t("quizEditor.addOption").toUpperCase()}
        value={option.text}
        onChange={(e) => onChange({ ...option, text: e.target.value })}
        sx={{
          "& .MuiInputBase-root": {
            color: option.isCorrect ? "#32cd32" : "#aaa",
            fontFamily: "monospace",
            fontSize: "0.85rem",
          },
          "& .MuiOutlinedInput-notchedOutline": { borderColor: "transparent" },
          "&:hover .MuiOutlinedInput-notchedOutline": {
            borderColor: "rgba(255,255,255,0.1)",
          },
        }}
      />

      <IconButton
        onClick={onDelete}
        sx={{ color: "#666", "&:hover": { color: "#ff4444" } }}
      >
        <Trash2 size={16} />
      </IconButton>
    </Box>
  );
};

export default OptionRow;
