import React, { useState, useEffect } from "react";
import {
  Box,
  Typography,
  CircularProgress,
  Snackbar,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { forgeApi } from "../../../api/client";
import type { QuizDefinition, QuizQuestion } from "../../../types/quiz";
import { RetroPanel } from "../RetroPanel";
import RetroButton from "../../RetroButton";
import QuizSidebar from "./QuizSidebar";
import QuestionCard from "./QuestionCard";
import { Plus, Eye, Save, ArrowLeft } from "lucide-react";

interface QuizEditorProps {
  missionId: string;
}

const DEFAULT_QUIZ: QuizDefinition = {
  config: {
    timeLimitSeconds: 600,
    allowNavigation: true,
    showSolutions: true,
  },
  questions: [],
};

const QuizEditor: React.FC<QuizEditorProps> = ({ missionId }) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [quizData, setQuizData] = useState<QuizDefinition>(DEFAULT_QUIZ);
  const [isPreviewOpen, setPreviewOpen] = useState(false);
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "success" as "success" | "error",
  });

  // 1. ADATOK LEKÉRÉSE
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

  // 2. MENTÉS MUTÁCIÓ
  const saveMutation = useMutation({
    mutationFn: (data: QuizDefinition) =>
      forgeApi.saveMissionFiles(missionId, {
        missionId,
        files: { "quiz.json": JSON.stringify(data, null, 2) },
      }),
    onSuccess: () => {
      setSnackbar({
        open: true,
        message: t("quiz.saveSuccess"),
        severity: "success",
      });
      queryClient.invalidateQueries({ queryKey: ["missionFiles", missionId] });
    },
    onError: (err: any) => {
      setSnackbar({
        open: true,
        message: err.message,
        severity: "error",
      });
    },
  });

  // 3. MŰVELETEK
  const addNewQuestion = () => {
    const newQuestion: QuizQuestion = {
      id: `q_${crypto.randomUUID()}`,
      text: "",
      points: 10,
      options: [
        { id: `o_${crypto.randomUUID()}`, text: "", isCorrect: false },
        { id: `o_${crypto.randomUUID()}`, text: "", isCorrect: false },
      ],
    };
    setQuizData((prev) => ({
      ...prev,
      questions: [...prev.questions, newQuestion],
    }));
  };

  const updateQuestion = (updated: QuizQuestion) => {
    setQuizData((prev) => ({
      ...prev,
      questions: prev.questions.map((q) => (q.id === updated.id ? updated : q)),
    }));
  };

  const deleteQuestion = (id: string) => {
    setQuizData((prev) => ({
      ...prev,
      questions: prev.questions.filter((q) => q.id !== id),
    }));
  };

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", mt: 10 }}>
        <CircularProgress color="#ffb000" />
      </Box>
    );
  }

  return (
    <RetroPanel
      title={t("quiz.editorTitle")}
      sx={{
        width: "98vw",
        height: "92vh",
        display: "flex",
        flexDirection: "column",
        p: 0,
        overflow: "hidden",
      }}
    >
      {/* TOP TOOLBAR */}
      <Box
        sx={{
          p: 1.5,
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          borderBottom: "2px solid #333",
          bgcolor: "#111",
        }}
      >
        <Box sx={{ display: "flex", gap: 3, ml: 2 }}>
          <RetroButton
            color="red"
            labelKey="starMap.back"
            size="small"
            onClick={() => window.history.back()}
          />
        </Box>

        <Box sx={{ display: "flex", gap: 3, mr: 2 }}>
          <RetroButton
            color="yellow"
            labelKey="quiz.preview"
            size="small"
            onClick={() => setPreviewOpen(true)}
          />
          <RetroButton
            color="green"
            labelKey="forge.saveButton"
            size="small"
            onClick={() => saveMutation.mutate(quizData)}
            disabled={saveMutation.isPending}
            active={saveMutation.isPending}
          />
        </Box>
      </Box>

      {/* WORKSPACE */}
      <Box sx={{ flexGrow: 1, display: "flex", overflow: "hidden" }}>
        <QuizSidebar
          config={quizData.config}
          onChange={(config) => setQuizData((prev) => ({ ...prev, config }))}
        />

        <Box
          sx={{
            flexGrow: 1,
            p: 4,
            overflowY: "auto",
            bgcolor: "#000",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
          }}
        >
          <Box sx={{ width: "100%", maxWidth: "900px" }}>
            {quizData.questions.length === 0 ? (
              <Box
                sx={{
                  mt: 10,
                  textAlign: "center",
                  border: "1px dashed #333",
                  p: 5,
                }}
              >
                <Typography sx={{ color: "#444", fontFamily: "monospace" }}>
                  {t("quiz.noQuestions").toUpperCase()}
                </Typography>
              </Box>
            ) : (
              quizData.questions.map((q, idx) => (
                <QuestionCard
                  key={q.id}
                  question={q}
                  index={idx}
                  onChange={updateQuestion}
                  onDelete={() => deleteQuestion(q.id)}
                />
              ))
            )}

            <Box sx={{ display: "flex", justifyContent: "center", mt: 4, mb: 10 }}>
              <RetroButton
                color="yellow"
                labelKey="quiz.addQuestion"
                onClick={addNewQuestion}
              />
            </Box>
          </Box>
        </Box>
      </Box>

      {/* PREVIEW DIALOG */}
      <Dialog
        fullWidth
        maxWidth="md"
        open={isPreviewOpen}
        onClose={() => setPreviewOpen(false)}
        PaperProps={{
          sx: { bgcolor: "#111", border: "2px solid #ffb000", borderRadius: 0 },
        }}
      >
        <DialogTitle sx={{ color: "#ffb000", fontFamily: "monospace", borderBottom: '1px solid #333' }}>
          [PREVIEW_MODE]
        </DialogTitle>
        <DialogContent sx={{ mt: 2 }}>
           <Typography sx={{ color: '#888', mb: 4, fontFamily: 'monospace', fontSize: '0.8rem' }}>
             SYSTEM_NOTE: This is a visual preview only. Answers are not saved.
           </Typography>
           <pre style={{ color: '#aaa', fontSize: '0.7rem' }}>
             {JSON.stringify(quizData, null, 2)}
           </pre>
        </DialogContent>
        <DialogActions sx={{ p: 3, borderTop: '1px solid #333' }}>
           <RetroButton color="red" labelKey="cancel" size="small" onClick={() => setPreviewOpen(false)} />
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert severity={snackbar.severity} variant="filled">
          {snackbar.message.toUpperCase()}
        </Alert>
      </Snackbar>
    </RetroPanel>
  );
};

export default QuizEditor;
