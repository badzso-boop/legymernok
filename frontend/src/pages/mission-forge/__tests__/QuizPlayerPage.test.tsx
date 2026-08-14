import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ThemeModeProvider } from "../../../theme/ThemeModeProvider";
import QuizPlayerPage from "../QuizPlayerPage";
import type { MissionResult } from "../../../types/quiz";
import type { QuizDefinition } from "../../../types/quiz";

// --- Mockok ---

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const mockedNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return { ...actual, useNavigate: () => mockedNavigate };
});

vi.mock("../../../api/client", () => ({
  quizApi: {
    startQuiz: vi.fn(),
    submitQuiz: vi.fn(),
  },
}));

vi.mock("@tanstack/react-query", async () => {
  const actual = await vi.importActual("@tanstack/react-query");
  return {
    ...actual,
    useQuery: vi.fn(),
    useMutation: vi.fn(),
    useQueryClient: vi.fn(() => ({ resetQueries: vi.fn() })),
  };
});

// QuizPlayer egyszerűsített mock: csak egy Submit gombot renderel
vi.mock("../../../components/forge/quiz/QuizPlayer", () => ({
  default: ({
    onSubmit,
  }: {
    onSubmit?: (answers: Record<string, string[]>) => void;
  }) => (
    <div data-testid="quiz-player-mock">
      <button onClick={() => onSubmit?.({ q1: ["o1"] })}>SUBMIT_QUIZ</button>
    </div>
  ),
}));

import { useQuery, useMutation } from "@tanstack/react-query";
const mockedUseQuery = vi.mocked(useQuery);
const mockedUseMutation = vi.mocked(useMutation);

// --- Test adatok ---

const mockQuizData: QuizDefinition = {
  config: { timeLimitSeconds: 300, allowNavigation: true, showSolutions: false },
  questions: [
    {
      id: "q1",
      text: "What is 2+2?",
      points: 10,
      options: [
        { id: "o1", text: "Four", isCorrect: true },
        { id: "o2", text: "Five", isCorrect: false },
      ],
    },
  ],
};

const mockResult: MissionResult = {
  id: "result-1",
  score: 8,
  maxScore: 10,
  percentage: 80,
  detailedAnswers: "{}",
  isLate: false,
  completedAt: "2026-03-14T10:00:00Z",
};

const renderPage = () =>
  render(
    <ThemeModeProvider>
      <MemoryRouter initialEntries={["/quiz/mission-1"]}>
        <Routes>
          <Route path="/quiz/:missionId" element={<QuizPlayerPage />} />
        </Routes>
      </MemoryRouter>
    </ThemeModeProvider>,
  );

describe("QuizPlayerPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("töltés közben CircularProgress látható", () => {
    mockedUseQuery.mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as any);
    mockedUseMutation.mockReturnValue({ mutate: vi.fn() } as any);

    renderPage();

    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });

  it("hiba esetén hibaüzenet jelenik meg", () => {
    mockedUseQuery.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error("Network error"),
    } as any);
    mockedUseMutation.mockReturnValue({ mutate: vi.fn() } as any);

    renderPage();

    expect(screen.getByText("ERROR_LOADING_MISSION_DATA")).toBeInTheDocument();
  });

  it("sikeres betöltés után a QuizPlayer megjelenik", () => {
    mockedUseQuery.mockReturnValue({
      data: mockQuizData,
      isLoading: false,
      error: null,
    } as any);
    mockedUseMutation.mockReturnValue({ mutate: vi.fn() } as any);

    renderPage();

    expect(screen.getByTestId("quiz-player-mock")).toBeInTheDocument();
  });

  it("beküldés után az eredmény képernyő megjelenik (score, percentage)", async () => {
    const mutate = vi.fn();
    let capturedOnSuccess: ((data: MissionResult) => void) | undefined;

    mockedUseQuery.mockReturnValue({
      data: mockQuizData,
      isLoading: false,
      error: null,
    } as any);

    mockedUseMutation.mockImplementation(({ onSuccess }: any) => {
      capturedOnSuccess = onSuccess;
      return { mutate } as any;
    });

    renderPage();

    // Szimuláljuk a beküldést
    fireEvent.click(screen.getByText("SUBMIT_QUIZ"));
    expect(mutate).toHaveBeenCalledWith({ q1: ["o1"] });

    // Szimuláljuk a sikeres API választ
    act(() => {
      capturedOnSuccess!(mockResult);
    });

    await waitFor(() => {
      expect(screen.getByText("MISSION_ACCOMPLISHED")).toBeInTheDocument();
      expect(screen.getByText(/8 \/ 10/)).toBeInTheDocument();
      expect(screen.getByText("80%")).toBeInTheDocument();
    });
  });

  it("409 (már beküldött) esetén is megjelenik az eredmény képernyő", async () => {
    let capturedOnError:
      | ((err: {
          response?: { status: number; data: { data: MissionResult } };
        }) => void)
      | undefined;

    mockedUseQuery.mockReturnValue({
      data: mockQuizData,
      isLoading: false,
      error: null,
    } as any);

    mockedUseMutation.mockImplementation(({ onError }: any) => {
      capturedOnError = onError;
      return { mutate: vi.fn() } as any;
    });

    renderPage();

    act(() => {
      capturedOnError!({
        response: {
          status: 409,
          data: { data: mockResult },
        },
      });
    });

    await waitFor(() => {
      expect(screen.getByText("MISSION_ACCOMPLISHED")).toBeInTheDocument();
      expect(screen.getByText("80%")).toBeInTheDocument();
    });
  });

  it("eredmény képernyőn a Back gomb navigate(-1)-t hív", async () => {
    let capturedOnSuccess: ((data: MissionResult) => void) | undefined;

    mockedUseQuery.mockReturnValue({
      data: mockQuizData,
      isLoading: false,
      error: null,
    } as any);

    mockedUseMutation.mockImplementation(({ onSuccess }: any) => {
      capturedOnSuccess = onSuccess;
      return { mutate: vi.fn() } as any;
    });

    renderPage();

    act(() => {
      capturedOnSuccess!(mockResult);
    });

    await waitFor(() =>
      expect(screen.getByText("MISSION_ACCOMPLISHED")).toBeInTheDocument(),
    );

    // RetroButton: a label div.label-plate-ben van, a <button> mellé renderelve
    const backBtn = screen
      .getByText("starMap.back")
      .closest(".button-group")!
      .querySelector("button")!;

    fireEvent.click(backBtn);
    expect(mockedNavigate).toHaveBeenCalledWith(-1);
  });
});
