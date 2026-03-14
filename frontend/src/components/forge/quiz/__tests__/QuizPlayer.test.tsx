import { render, screen, fireEvent, act } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach, afterEach } from "vitest";
import QuizPlayer from "../QuizPlayer";
import type { QuizDefinition } from "../../../../types/quiz";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

// RetroButton: a label-szöveg div.label-plate-ben van, nem a <button>-ban.
// Segédfüggvény: text alapján megkeresi a szülő .button-group-ot, majd azon belül a <button>-t.
const getRetroBtn = (labelText: string): HTMLButtonElement =>
  screen
    .getByText(labelText)
    .closest(".button-group")!
    .querySelector("button")!;

// --- Test adatok ---

const twoQuestionQuiz: QuizDefinition = {
  config: { timeLimitSeconds: 300, allowNavigation: true, showSolutions: false },
  questions: [
    {
      id: "q1",
      text: "What is 2+2?",
      points: 10,
      options: [
        { id: "o1", text: "Three", isCorrect: false },
        { id: "o2", text: "Four", isCorrect: true },
        { id: "o3", text: "Five", isCorrect: false },
      ],
    },
    {
      id: "q2",
      text: "Capital of France?",
      points: 5,
      options: [
        { id: "o4", text: "London", isCorrect: false },
        { id: "o5", text: "Paris", isCorrect: true },
      ],
    },
  ],
};

const singleQuestionQuiz: QuizDefinition = {
  config: { timeLimitSeconds: 300, allowNavigation: true, showSolutions: false },
  questions: [
    {
      id: "q1",
      text: "Only question",
      points: 5,
      options: [
        { id: "o1", text: "Yes", isCorrect: true },
        { id: "o2", text: "No", isCorrect: false },
      ],
    },
  ],
};

const multiSelectQuiz: QuizDefinition = {
  config: { timeLimitSeconds: 300, allowNavigation: true, showSolutions: false },
  questions: [
    {
      id: "q1",
      text: "Select even numbers",
      points: 10,
      options: [
        { id: "o1", text: "One", isCorrect: false },
        { id: "o2", text: "Two", isCorrect: true },
        { id: "o3", text: "Four", isCorrect: true },
        { id: "o4", text: "Five", isCorrect: false },
      ],
    },
  ],
};

const shortTimerQuiz: QuizDefinition = {
  config: { timeLimitSeconds: 3, allowNavigation: true, showSolutions: false },
  questions: [
    {
      id: "q1",
      text: "Quick question",
      points: 5,
      options: [
        { id: "o1", text: "Alpha", isCorrect: true },
        { id: "o2", text: "Beta", isCorrect: false },
      ],
    },
  ],
};

describe("QuizPlayer", () => {
  describe("Megjelenítés", () => {
    it("megjeleníti az első kérdés szövegét", () => {
      render(<QuizPlayer data={twoQuestionQuiz} />);
      expect(screen.getByText("What is 2+2?")).toBeInTheDocument();
    });

    it("megjeleníti a válaszlehetőségeket", () => {
      render(<QuizPlayer data={twoQuestionQuiz} />);
      expect(screen.getByText("Three")).toBeInTheDocument();
      expect(screen.getByText("Four")).toBeInTheDocument();
      expect(screen.getByText("Five")).toBeInTheDocument();
    });

    it("az első kérdésnél a Prev gomb disabled", () => {
      render(<QuizPlayer data={twoQuestionQuiz} />);
      expect(getRetroBtn("quiz.prev")).toBeDisabled();
    });

    it("több kérdésnél Next gomb jelenik meg (nem Finish)", () => {
      render(<QuizPlayer data={twoQuestionQuiz} />);
      expect(screen.queryByText("quiz.finish")).not.toBeInTheDocument();
      expect(screen.getByText("quiz.next")).toBeInTheDocument();
    });

    it("egyetlen kérdésnél Finish gomb jelenik meg (nem Next)", () => {
      render(<QuizPlayer data={singleQuestionQuiz} />);
      expect(screen.queryByText("quiz.next")).not.toBeInTheDocument();
      expect(screen.getByText("quiz.finish")).toBeInTheDocument();
    });

    it("preview módban megjelenik a bezárás gomb", () => {
      render(
        <QuizPlayer data={singleQuestionQuiz} isPreview onClose={vi.fn()} />,
      );
      expect(screen.getByText("quiz.closePreview")).toBeInTheDocument();
    });

    it("haladásjelző progress szöveget mutat a haladásjelzőben", () => {
      render(<QuizPlayer data={twoQuestionQuiz} />);
      // A haladásjelző span tartalmazza: "[quizEditor.progress : 1 / 2]"
      expect(screen.getByText(/quizEditor\.progress/)).toBeInTheDocument();
    });
  });

  describe("Navigáció", () => {
    it("Next gombra kattintva a következő kérdés jelenik meg", () => {
      render(<QuizPlayer data={twoQuestionQuiz} />);

      expect(screen.getByText("What is 2+2?")).toBeInTheDocument();

      fireEvent.click(getRetroBtn("quiz.next"));

      expect(screen.getByText("Capital of France?")).toBeInTheDocument();
      expect(screen.queryByText("What is 2+2?")).not.toBeInTheDocument();
    });

    it("az utolsó kérdésnél Next helyett Finish gomb van", () => {
      render(<QuizPlayer data={twoQuestionQuiz} />);

      fireEvent.click(getRetroBtn("quiz.next"));

      expect(screen.getByText("quiz.finish")).toBeInTheDocument();
      expect(screen.queryByText("quiz.next")).not.toBeInTheDocument();
    });

    it("Prev gomb visszavisz az előző kérdésre", () => {
      render(<QuizPlayer data={twoQuestionQuiz} />);

      fireEvent.click(getRetroBtn("quiz.next"));
      expect(screen.getByText("Capital of France?")).toBeInTheDocument();

      fireEvent.click(getRetroBtn("quiz.prev"));
      expect(screen.getByText("What is 2+2?")).toBeInTheDocument();
    });

    it("az első kérdésnél Prev nem megy 0 alá (védelem)", () => {
      render(<QuizPlayer data={twoQuestionQuiz} />);

      // Prev disabled az első kérdésnél → kattintás nem navigál
      fireEvent.click(getRetroBtn("quiz.prev"));

      expect(screen.getByText("What is 2+2?")).toBeInTheDocument();
    });
  });

  describe("Single-select válaszok", () => {
    it("radio gomb kattintásra kiválasztódik a válasz", () => {
      render(<QuizPlayer data={twoQuestionQuiz} />);

      const radio = screen.getByDisplayValue("o1");
      fireEvent.click(radio);

      expect(radio).toBeChecked();
    });

    it("másik radio gombra kattintva az előző deselect-álódik", () => {
      render(<QuizPlayer data={twoQuestionQuiz} />);

      const opt1 = screen.getByDisplayValue("o1");
      const opt2 = screen.getByDisplayValue("o2");

      fireEvent.click(opt1);
      expect(opt1).toBeChecked();

      fireEvent.click(opt2);
      expect(opt2).toBeChecked();
      expect(opt1).not.toBeChecked();
    });
  });

  describe("Multi-select válaszok", () => {
    it("több checkbox egyszerre kijelölhető", () => {
      render(<QuizPlayer data={multiSelectQuiz} />);

      const checkboxes = screen.getAllByRole("checkbox");
      // o2="Two" és o3="Four" isCorrect=true → MULTI_SELECTION_MODE
      fireEvent.click(checkboxes[1]); // Two
      fireEvent.click(checkboxes[2]); // Four

      expect(checkboxes[1]).toBeChecked();
      expect(checkboxes[2]).toBeChecked();
    });

    it("már kijelölt checkbox újra kattintásra deselect-álódik", () => {
      render(<QuizPlayer data={multiSelectQuiz} />);

      const checkboxes = screen.getAllByRole("checkbox");
      fireEvent.click(checkboxes[1]);
      expect(checkboxes[1]).toBeChecked();

      fireEvent.click(checkboxes[1]);
      expect(checkboxes[1]).not.toBeChecked();
    });
  });

  describe("Beküldés (onSubmit)", () => {
    it("Finish gombra kattintva onSubmit meghívódik az összegyűjtött válaszokkal", () => {
      const mockSubmit = vi.fn();
      render(<QuizPlayer data={singleQuestionQuiz} onSubmit={mockSubmit} />);

      fireEvent.click(screen.getByDisplayValue("o1"));
      fireEvent.click(getRetroBtn("quiz.finish"));

      expect(mockSubmit).toHaveBeenCalledTimes(1);
      expect(mockSubmit).toHaveBeenCalledWith({ q1: ["o1"] });
    });

    it("Finish gombra üres válasszal is meghívható (0 kijelölt opció)", () => {
      const mockSubmit = vi.fn();
      render(<QuizPlayer data={singleQuestionQuiz} onSubmit={mockSubmit} />);

      fireEvent.click(getRetroBtn("quiz.finish"));

      expect(mockSubmit).toHaveBeenCalledWith({});
    });
  });

  describe("Időzítő és auto-submit", () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it("időzítő lejártakor auto-submit meghívódik", async () => {
      const mockSubmit = vi.fn();
      render(<QuizPlayer data={shortTimerQuiz} onSubmit={mockSubmit} />);

      await act(async () => {
        vi.advanceTimersByTime(3000);
      });

      expect(mockSubmit).toHaveBeenCalledTimes(1);
    });

    it("időzítő lejártakor a Finish gomb disabled-dé válik", async () => {
      render(<QuizPlayer data={shortTimerQuiz} onSubmit={vi.fn()} />);

      await act(async () => {
        vi.advanceTimersByTime(3000);
      });

      expect(getRetroBtn("quiz.finish")).toBeDisabled();
    });

    it("preview módban az időzítő lejártakor NEM hívódik meg onSubmit", async () => {
      const mockSubmit = vi.fn();
      render(
        <QuizPlayer
          data={shortTimerQuiz}
          isPreview
          onClose={vi.fn()}
          onSubmit={mockSubmit}
        />,
      );

      await act(async () => {
        vi.advanceTimersByTime(3000);
      });

      expect(mockSubmit).not.toHaveBeenCalled();
    });

    it("onClose meghívódik preview-ban a bezárás gombra kattintva", () => {
      vi.useRealTimers(); // Nincs szükség fake timerre ebben a tesztben
      const mockClose = vi.fn();
      render(
        <QuizPlayer data={shortTimerQuiz} isPreview onClose={mockClose} />,
      );

      fireEvent.click(getRetroBtn("quiz.closePreview"));

      expect(mockClose).toHaveBeenCalledTimes(1);
    });
  });
});
