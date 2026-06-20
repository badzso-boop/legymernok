import { render, screen, fireEvent } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import QuestionCard from "../QuestionCard";
import type { QuizQuestion } from "../../../../types/quiz";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

// crypto.randomUUID-t mockolni kell, mert jsdom-ban nem elérhető
let uuidCounter = 0;
vi.stubGlobal("crypto", {
  randomUUID: () => `test-uuid-${++uuidCounter}`,
});

const makeQuestion = (overrides: Partial<QuizQuestion> = {}): QuizQuestion => ({
  id: "q1",
  text: "What is 2+2?",
  points: 10,
  options: [
    { id: "o1", text: "3", isCorrect: false },
    { id: "o2", text: "4", isCorrect: true },
    { id: "o3", text: "5", isCorrect: false },
  ],
  ...overrides,
});

describe("QuestionCard", () => {
  beforeEach(() => {
    uuidCounter = 0;
  });

  it("renders the question header with correct index", () => {
    render(
      <QuestionCard
        question={makeQuestion()}
        index={2}
        onChange={vi.fn()}
        onDelete={vi.fn()}
      />,
    );
    expect(screen.getByText("[QUESTION_03]")).toBeInTheDocument();
  });

  it("renders question text in the textarea", () => {
    render(
      <QuestionCard
        question={makeQuestion()}
        index={0}
        onChange={vi.fn()}
        onDelete={vi.fn()}
      />,
    );
    expect(screen.getByDisplayValue("What is 2+2?")).toBeInTheDocument();
  });

  it("calls onDelete when the header trash button is clicked", () => {
    const handleDelete = vi.fn();
    render(
      <QuestionCard
        question={makeQuestion()}
        index={0}
        onChange={vi.fn()}
        onDelete={handleDelete}
      />,
    );

    // The header IconButton has the Trash2 icon — it's the first (and only) IconButton in the header
    const deleteButtons = screen.getAllByRole("button");
    // Header delete is the last icon button (after the option delete buttons)
    // Actually the header Trash2 is an IconButton, option delete buttons are also IconButtons
    // The header one comes first in the DOM
    fireEvent.click(deleteButtons[0]);

    expect(handleDelete).toHaveBeenCalledTimes(1);
  });

  it("calls onChange with new option when add option is clicked", () => {
    const handleChange = vi.fn();
    const question = makeQuestion({ options: [] });

    render(
      <QuestionCard
        question={question}
        index={0}
        onChange={handleChange}
        onDelete={vi.fn()}
      />,
    );

    const addOptionBox = screen.getByText(/quizEditor.addOption/i);
    fireEvent.click(addOptionBox);

    expect(handleChange).toHaveBeenCalledTimes(1);
    const updatedQuestion = handleChange.mock.calls[0][0] as QuizQuestion;
    expect(updatedQuestion.options).toHaveLength(1);
    expect(updatedQuestion.options[0].isCorrect).toBe(false);
    expect(updatedQuestion.options[0].text).toBe("");
  });

  it("does not show add option when 5 options exist", () => {
    const question = makeQuestion({
      options: [
        { id: "o1", text: "A", isCorrect: false },
        { id: "o2", text: "B", isCorrect: false },
        { id: "o3", text: "C", isCorrect: false },
        { id: "o4", text: "D", isCorrect: false },
        { id: "o5", text: "E", isCorrect: false },
      ],
    });

    render(
      <QuestionCard
        question={question}
        index={0}
        onChange={vi.fn()}
        onDelete={vi.fn()}
      />,
    );

    expect(
      screen.queryByText(/quizEditor.addOption/i),
    ).not.toBeInTheDocument();
  });

  it("shows MULTI_SELECTION_MODE when 2+ options are correct", () => {
    const question = makeQuestion({
      options: [
        { id: "o1", text: "A", isCorrect: true },
        { id: "o2", text: "B", isCorrect: true },
        { id: "o3", text: "C", isCorrect: false },
      ],
    });

    render(
      <QuestionCard
        question={question}
        index={0}
        onChange={vi.fn()}
        onDelete={vi.fn()}
      />,
    );

    expect(screen.getByText("MULTI_SELECTION_MODE")).toBeInTheDocument();
  });

  it("shows SINGLE_SELECTION_MODE when only 1 option is correct", () => {
    render(
      <QuestionCard
        question={makeQuestion()}
        index={0}
        onChange={vi.fn()}
        onDelete={vi.fn()}
      />,
    );

    expect(screen.getByText("SINGLE_SELECTION_MODE")).toBeInTheDocument();
  });

  it("calls onChange with updated option when an option is modified", () => {
    const handleChange = vi.fn();
    render(
      <QuestionCard
        question={makeQuestion()}
        index={0}
        onChange={handleChange}
        onDelete={vi.fn()}
      />,
    );

    // First option text input is "3"
    const input = screen.getByDisplayValue("3");
    fireEvent.change(input, { target: { value: "2" } });

    expect(handleChange).toHaveBeenCalledTimes(1);
    const updated = handleChange.mock.calls[0][0] as QuizQuestion;
    expect(updated.options[0].text).toBe("2");
  });

  it("calls onChange without the deleted option when option is removed", () => {
    const handleChange = vi.fn();
    render(
      <QuestionCard
        question={makeQuestion()}
        index={0}
        onChange={handleChange}
        onDelete={vi.fn()}
      />,
    );

    // The first trash button in the option rows (index 1 since index 0 = header delete)
    const allButtons = screen.getAllByRole("button");
    // Header delete = allButtons[0], then each OptionRow has 1 IconButton (delete)
    fireEvent.click(allButtons[1]); // delete first option (o1, text "3")

    expect(handleChange).toHaveBeenCalledTimes(1);
    const updated = handleChange.mock.calls[0][0] as QuizQuestion;
    expect(updated.options).toHaveLength(2);
    expect(updated.options.find((o) => o.id === "o1")).toBeUndefined();
  });
});
