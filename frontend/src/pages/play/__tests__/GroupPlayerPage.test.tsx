import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeModeProvider } from "../../../theme/ThemeModeProvider";
import GroupPlayerPage from "../GroupPlayerPage";

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
  missionGroupApi: {
    getById: vi.fn(),
  },
  groupProgressApi: {
    get: vi.fn(),
    start: vi.fn(),
    completeStep: vi.fn(),
  },
}));

// Gyermekkomponensek egyszerűsített mockjai
vi.mock("../../../components/play/ContentMissionView", () => ({
  default: ({ onComplete }: { onComplete?: () => void }) => (
    <div data-testid="content-mission-view">
      <button onClick={onComplete}>NEXT_CONTENT</button>
    </div>
  ),
}));

vi.mock("../../../components/play/FillInBlankView", () => ({
  default: ({ onComplete }: { onComplete: () => void }) => (
    <div data-testid="fill-in-blank-view">
      <button onClick={onComplete}>NEXT_FIB</button>
    </div>
  ),
}));

vi.mock("../../../components/forge/quiz/QuizPlayerComponent", () => ({
  default: ({ onComplete }: { onComplete: (r: any) => void }) => (
    <div data-testid="quiz-player-component">
      <button onClick={() => onComplete({})}>NEXT_QUIZ</button>
    </div>
  ),
}));

import { missionGroupApi, groupProgressApi } from "../../../api/client";
const mockedGetGroup = vi.mocked(missionGroupApi.getById);
const mockedGetProgress = vi.mocked(groupProgressApi.get);
const mockedStartProgress = vi.mocked(groupProgressApi.start);
const mockedCompleteStep = vi.mocked(groupProgressApi.completeStep);

// --- Test adatok ---

const mockGroup = {
  id: "g1",
  name: "Test Group",
  description: null,
  starSystemId: "ss1",
  starSystemName: "Test System",
  orderIndex: 1,
  missions: [
    {
      id: "m1",
      name: "Content Mission",
      missionType: "CONTENT",
      difficulty: "EASY",
      starSystemId: "ss1",
      descriptionMarkdown: "",
      templateRepositoryUrl: null,
      orderIndex: 1,
      groupId: "g1",
      groupOrder: 1,
      createdAt: new Date().toISOString(),
    },
    {
      id: "m2",
      name: "FIB Mission",
      missionType: "FILL_IN_BLANK",
      difficulty: "EASY",
      starSystemId: "ss1",
      descriptionMarkdown: "",
      templateRepositoryUrl: null,
      orderIndex: 2,
      groupId: "g1",
      groupOrder: 2,
      createdAt: new Date().toISOString(),
    },
  ],
};

const makeProgress = (nextMissionId: string | null, completed = false) => ({
  groupId: "g1",
  completed,
  nextMissionId,
  completedCount: 0,
  totalCount: 2,
  startedAt: new Date().toISOString(),
  lastUpdatedAt: new Date().toISOString(),
  completedAt: null,
});

// Segédfüggvény: route-os renderelés
const renderPage = () => {
  const qc = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={qc}>
      <ThemeModeProvider>
        <MemoryRouter initialEntries={["/play/group/g1"]}>
          <Routes>
            <Route path="/play/group/:groupId" element={<GroupPlayerPage />} />
          </Routes>
        </MemoryRouter>
      </ThemeModeProvider>
    </QueryClientProvider>,
  );
};

// --- Tesztek ---

describe("GroupPlayerPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetGroup.mockResolvedValue(mockGroup as any);
    mockedStartProgress.mockResolvedValue(undefined as any);
    mockedCompleteStep.mockResolvedValue(makeProgress("m2") as any);
  });

  it("404 progress → groupProgressApi.start automatikusan hívódik", async () => {
    mockedGetProgress
      .mockRejectedValueOnce({ response: { status: 404 } })
      .mockResolvedValue(makeProgress("m1") as any);

    renderPage();

    await waitFor(() => {
      expect(mockedStartProgress).toHaveBeenCalledWith("g1");
    });
  });

  it("CONTENT típusú nextMissionId → ContentMissionView renderelődik", async () => {
    mockedGetProgress.mockResolvedValue(makeProgress("m1") as any);

    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId("content-mission-view")).toBeTruthy();
    });
  });

  it("FILL_IN_BLANK típusú nextMissionId → FillInBlankView renderelődik", async () => {
    mockedGetProgress.mockResolvedValue(makeProgress("m2") as any);

    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId("fill-in-blank-view")).toBeTruthy();
    });
  });

  it("handleCompleteStep → groupProgressApi.completeStep hívódik", async () => {
    mockedGetProgress.mockResolvedValue(makeProgress("m1") as any);
    mockedCompleteStep.mockResolvedValue(makeProgress("m2") as any);

    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId("content-mission-view")).toBeTruthy();
    });

    // Kattintunk a "NEXT_CONTENT" gombra
    const nextBtn = screen.getByText("NEXT_CONTENT");
    fireEvent.click(nextBtn);

    await waitFor(() => {
      expect(mockedCompleteStep).toHaveBeenCalledWith("g1");
    });
  });

  it("progress.completed: true → befejezési képernyő jelenik meg", async () => {
    mockedGetProgress.mockResolvedValue(makeProgress(null, true) as any);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/play\.groupCompleted/)).toBeTruthy();
    });
  });
});
