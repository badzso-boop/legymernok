import React from "react";
import {
  Box,
  Typography,
  TextField,
  FormControlLabel,
  Checkbox,
  Divider,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import type { QuizConfig } from "../../../types/quiz";

interface QuizSidebarProps {
  config: QuizConfig;
  onChange: (newConfig: QuizConfig) => void;
}

const QuizSidebar: React.FC<QuizSidebarProps> = ({ config, onChange }) => {
  const { t } = useTranslation();

  const handleChange = (field: keyof QuizConfig, value: any) => {
    onChange({ ...config, [field]: value });
  };

  const inputSx = {
    "& .MuiInputBase-root": {
      color: "#ffb000",
      fontFamily: "monospace",
      fontSize: "0.9rem",
    },
    "& .MuiInputLabel-root": { color: "#888", fontFamily: "monospace" },
    "& .MuiOutlinedInput-notchedOutline": { borderColor: "#333" },
    "&:hover .MuiOutlinedInput-notchedOutline": { borderColor: "#ffb000" },
    "& .Mui-focused .MuiOutlinedInput-notchedOutline": {
      borderColor: "#ffb000",
    },
    bgcolor: "rgba(0,0,0,0.3)",
    mb: 3,
  };

  return (
    <Box
      sx={{
        width: "300px",
        minWidth: "300px",
        height: "100%",
        bgcolor: "#0a0a0a",
        borderRight: "2px solid #333",
        p: 3,
        display: "flex",
        flexDirection: "column",
        gap: 2,
      }}
    >
      <Typography
        variant="caption"
        sx={{ color: "#888", fontWeight: "bold", mb: 2, display: "block" }}
      >
        [GLOBAL_CONFIG]
      </Typography>

      <TextField
        label={t("quiz.timeLimit").toUpperCase()}
        type="number"
        variant="outlined"
        size="small"
        fullWidth
        value={config.timeLimitSeconds}
        onChange={(e) =>
          handleChange("timeLimitSeconds", parseInt(e.target.value) || 0)
        }
        sx={inputSx}
      />

      <Divider sx={{ bgcolor: "#222", mb: 2 }} />

      <FormControlLabel
        control={
          <Checkbox
            checked={config.allowNavigation}
            onChange={(e) => handleChange("allowNavigation", e.target.checked)}
            sx={{
              color: "#444",
              "&.Mui-checked": { color: "#ffb000" },
            }}
          />
        }
        label={
          <Typography
            sx={{
              color: "#aaa",
              fontFamily: "monospace",
              fontSize: "0.8rem",
            }}
          >
            {t("quiz.allowBack").toUpperCase()}
          </Typography>
        }
        sx={{ mb: 1 }}
      />

      <FormControlLabel
        control={
          <Checkbox
            checked={config.showSolutions}
            onChange={(e) => handleChange("showSolutions", e.target.checked)}
            sx={{
              color: "#444",
              "&.Mui-checked": { color: "#ffb000" },
            }}
          />
        }
        label={
          <Typography
            sx={{
              color: "#aaa",
              fontFamily: "monospace",
              fontSize: "0.8rem",
            }}
          >
            {t("quiz.showResults").toUpperCase()}
          </Typography>
        }
      />
    </Box>
  );
};

export default QuizSidebar;
