import React, { useEffect, useState } from "react";
import { Box, Button, Snackbar, Alert, Dialog, DialogContent, CircularProgress } from "@mui/material";
import { Save as SaveIcon } from "@mui/icons-material";
import { useTranslation } from "react-i18next";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { forgeApi, quizApi } from "../../../api/client";
import type { QuizDefinition } from "../../../types/quiz";
import QuizPlayer from "../../forge/quiz/QuizPlayer";
import QuizBuilder from "./QuizBuilder";

interface QuizContentEditorProps {
  missionId: string;
}

const DEFAULT_QUIZ: QuizDefinition = {
  config: { timeLimitSeconds: 600, allowNavigation: true, showSolutions: true },
  questions: [],
};

/**
 * A quiz.json betöltése/mentése + a beágyazott QuizBuilder — a régi
 * (teljes-oldalas) QuizEditor tartalom-rétege, page-chrome nélkül, hogy a
 * MissionEditorPage-be ágyazható legyen (terv 4.1/4.3).
 */
export const QuizContentEditor: React.FC<QuizContentEditorProps> = ({ missionId }) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [quizData, setQuizData] = useState<QuizDefinition>(DEFAULT_QUIZ);
  const [isPreviewOpen, setPreviewOpen] = useState(false);
  const [snackbar, setSnackbar] = useState({ open: false, message: "", severity: "success" as "success" | "error" });

  const { data: fetchedFiles, isLoading } = useQuery({
    queryKey: ["missionFiles", missionId],
    queryFn: () => forgeApi.getMissionFiles(missionId),
  });

  useEffect(() => {
    if (fetchedFiles && fetchedFiles["quiz.json"]) {
      try {
        setQuizData(JSON.parse(fetchedFiles["quiz.json"]));
      } catch (e) {
        console.error("Failed to parse quiz.json", e);
      }
    }
  }, [fetchedFiles]);

  const clearSessionsMutation = useMutation({
    mutationFn: () => quizApi.clearSessions(missionId),
    onSuccess: () => setSnackbar({ open: true, message: t("quizEditor.sessionResetComplete"), severity: "success" }),
    onError: (err: any) => setSnackbar({ open: true, message: err.message, severity: "error" }),
  });

  const handleClearSessions = () => {
    if (window.confirm(t("quizEditor.clearSessionsConfirm"))) {
      clearSessionsMutation.mutate();
    }
  };

  const saveMutation = useMutation({
    mutationFn: (data: QuizDefinition) =>
      forgeApi.saveMissionFiles(missionId, { missionId, files: { "quiz.json": JSON.stringify(data, null, 2) } }),
    onSuccess: () => {
      setSnackbar({ open: true, message: t("quizEditor.saveSuccess"), severity: "success" });
      queryClient.invalidateQueries({ queryKey: ["missionFiles", missionId] });
    },
    onError: (err: any) => setSnackbar({ open: true, message: err.message, severity: "error" }),
  });

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <QuizBuilder quizData={quizData} onChange={setQuizData} />

      <Box sx={{ display: "flex", gap: 2, mt: 2 }}>
        <Button variant="outlined" color="error" onClick={handleClearSessions} disabled={clearSessionsMutation.isPending}>
          {t("quizEditor.clearSessions")}
        </Button>
        <Button variant="outlined" onClick={() => setPreviewOpen(true)}>
          {t("quizEditor.preview")}
        </Button>
        <Button
          variant="contained"
          startIcon={<SaveIcon />}
          onClick={() => saveMutation.mutate(quizData)}
          disabled={saveMutation.isPending}
          data-cy="quiz-save-btn"
        >
          {t("save")}
        </Button>
      </Box>

      <Dialog fullWidth maxWidth="lg" open={isPreviewOpen} onClose={() => setPreviewOpen(false)}>
        <DialogContent sx={{ p: 0, overflow: "hidden", height: "80vh" }}>
          <QuizPlayer data={quizData} isPreview={true} onClose={() => setPreviewOpen(false)} />
        </DialogContent>
      </Dialog>

      <Snackbar open={snackbar.open} autoHideDuration={4000} onClose={() => setSnackbar((s) => ({ ...s, open: false }))}>
        <Alert severity={snackbar.severity}>{snackbar.message}</Alert>
      </Snackbar>
    </Box>
  );
};

export default QuizContentEditor;
