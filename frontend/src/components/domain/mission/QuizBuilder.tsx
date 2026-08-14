import React from "react";
import { Box, Typography } from "@mui/material";
import { Plus } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { QuizDefinition, QuizQuestion } from "../../../types/quiz";
import QuestionCard from "../../forge/quiz/QuestionCard";
import QuizSidebar from "../../forge/quiz/QuizSidebar";

interface QuizBuilderProps {
  quizData: QuizDefinition;
  onChange: (data: QuizDefinition) => void;
}

/**
 * Beágyazható, form-alapú kvíz-szerkesztő — a nyers Monaco/quiz.json
 * szerkesztés helyett (terv 4.3). A sorrendezés fel/le gombokkal megy, nem
 * drag-handle-lel, kifejezetten mobil-kompatibilitás miatt.
 */
export const QuizBuilder: React.FC<QuizBuilderProps> = ({ quizData, onChange }) => {
  const { t } = useTranslation();

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
    onChange({ ...quizData, questions: [...quizData.questions, newQuestion] });
  };

  const updateQuestion = (updated: QuizQuestion) => {
    onChange({
      ...quizData,
      questions: quizData.questions.map((q) => (q.id === updated.id ? updated : q)),
    });
  };

  const deleteQuestion = (id: string) => {
    onChange({ ...quizData, questions: quizData.questions.filter((q) => q.id !== id) });
  };

  const moveQuestion = (index: number, direction: -1 | 1) => {
    const target = index + direction;
    if (target < 0 || target >= quizData.questions.length) return;
    const next = [...quizData.questions];
    [next[index], next[target]] = [next[target], next[index]];
    onChange({ ...quizData, questions: next });
  };

  return (
    <Box sx={{ display: "flex", gap: 3, flexWrap: "wrap" }}>
      <QuizSidebar config={quizData.config} onChange={(config) => onChange({ ...quizData, config })} />

      <Box sx={{ flex: 1, minWidth: 280 }}>
        {quizData.questions.length === 0 ? (
          <Box sx={{ textAlign: "center", border: "1px dashed #333", p: 5 }}>
            <Typography sx={{ color: "#666" }}>{t("quizEditor.noQuestions")}</Typography>
          </Box>
        ) : (
          quizData.questions.map((q, idx) => (
            <QuestionCard
              key={q.id}
              question={q}
              index={idx}
              onChange={updateQuestion}
              onDelete={() => deleteQuestion(q.id)}
              onMoveUp={() => moveQuestion(idx, -1)}
              onMoveDown={() => moveQuestion(idx, 1)}
              canMoveUp={idx > 0}
              canMoveDown={idx < quizData.questions.length - 1}
            />
          ))
        )}

        <Box sx={{ display: "flex", justifyContent: "center", mt: 2 }}>
          <Box
            onClick={addNewQuestion}
            data-cy="quiz-add-question-btn"
            sx={{
              p: 1.5,
              border: "1px dashed #666",
              display: "flex",
              alignItems: "center",
              gap: 1,
              cursor: "pointer",
              color: "#888",
              "&:hover": { color: "#fff", borderColor: "#fff" },
            }}
          >
            <Plus size={16} />
            {t("quizEditor.addQuestion")}
          </Box>
        </Box>
      </Box>
    </Box>
  );
};

export default QuizBuilder;
