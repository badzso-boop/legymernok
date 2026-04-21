import { render, screen, fireEvent } from "@testing-library/react";
import { vi, describe, it, expect } from "vitest";
import OptionRow from "../OptionRow";
import type { QuizOption } from "../../../../types/quiz";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const mockOption: QuizOption = {
  id: "opt-1",
  text: "Paris",
  isCorrect: false,
};

describe("OptionRow", () => {
  it("renders option text", () => {
    render(
      <OptionRow
        option={mockOption}
        onChange={vi.fn()}
        onDelete={vi.fn()}
      />,
    );
    expect(screen.getByDisplayValue("Paris")).toBeInTheDocument();
  });

  it("calls onChange with isCorrect=true when checkbox is checked", () => {
    const handleChange = vi.fn();
    render(
      <OptionRow
        option={mockOption}
        onChange={handleChange}
        onDelete={vi.fn()}
      />,
    );

    const checkbox = screen.getByRole("checkbox");
    fireEvent.click(checkbox);

    expect(handleChange).toHaveBeenCalledWith({
      ...mockOption,
      isCorrect: true,
    });
  });

  it("calls onChange with isCorrect=false when correct option is unchecked", () => {
    const correctOption: QuizOption = { ...mockOption, isCorrect: true };
    const handleChange = vi.fn();

    render(
      <OptionRow
        option={correctOption}
        onChange={handleChange}
        onDelete={vi.fn()}
      />,
    );

    const checkbox = screen.getByRole("checkbox");
    fireEvent.click(checkbox);

    expect(handleChange).toHaveBeenCalledWith({
      ...correctOption,
      isCorrect: false,
    });
  });

  it("calls onChange when text input changes", () => {
    const handleChange = vi.fn();
    render(
      <OptionRow
        option={mockOption}
        onChange={handleChange}
        onDelete={vi.fn()}
      />,
    );

    const input = screen.getByDisplayValue("Paris");
    fireEvent.change(input, { target: { value: "Berlin" } });

    expect(handleChange).toHaveBeenCalledWith({
      ...mockOption,
      text: "Berlin",
    });
  });

  it("calls onDelete when trash button is clicked", () => {
    const handleDelete = vi.fn();
    render(
      <OptionRow
        option={mockOption}
        onChange={vi.fn()}
        onDelete={handleDelete}
      />,
    );

    // The delete button is the only icon button in OptionRow
    const deleteBtn = screen.getByRole("button");
    fireEvent.click(deleteBtn);

    expect(handleDelete).toHaveBeenCalledTimes(1);
  });

  it("shows checkbox as checked for correct option", () => {
    const correctOption: QuizOption = { ...mockOption, isCorrect: true };
    render(
      <OptionRow
        option={correctOption}
        onChange={vi.fn()}
        onDelete={vi.fn()}
      />,
    );

    expect(screen.getByRole("checkbox")).toBeChecked();
  });
});
