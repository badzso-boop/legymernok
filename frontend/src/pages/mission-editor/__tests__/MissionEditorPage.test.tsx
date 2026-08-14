import { render, screen, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeModeProvider } from "../../../theme/ThemeModeProvider";
import MissionEditorPage from "../MissionEditorPage";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock("../../../context/ChatContext", () => ({
  useChatContext: () => ({
    formFields: {},
    setFormFields: vi.fn(),
    registerFillCallback: vi.fn(),
    triggerFill: vi.fn(),
  }),
}));

vi.mock("../../../api/client", () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
  forgeApi: {
    getMyStarSystems: vi.fn(),
    getMissionById: vi.fn(),
    getMissionFiles: vi.fn().mockResolvedValue({}),
  },
  starSystemApi: {
    create: vi.fn(),
  },
}));

import apiClient from "../../../api/client";
const mockedApiClient = apiClient as any;

const createWrapper = (initialEntries: string[], path: string, mode: "admin" | "forge") => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } } });
  return render(
    <ThemeModeProvider>
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={initialEntries}>
          <Routes>
            <Route path={path} element={<MissionEditorPage mode={mode} />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeModeProvider>,
  );
};

describe("MissionEditorPage — admin mode", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads data for editing", async () => {
    const mockMission = {
      id: "1",
      name: "Test Mission",
      starSystemId: "s1",
      orderIndex: 5,
      difficulty: "HARD",
      missionType: "CODING",
      descriptionMarkdown: "",
    };
    const mockSystems = [{ id: "s1", name: "System 1" }];

    mockedApiClient.get.mockImplementation((url: string) => {
      if (url.includes("/missions/1")) return Promise.resolve({ data: mockMission });
      if (url.includes("/star-systems")) return Promise.resolve({ data: mockSystems });
      return Promise.reject(new Error("unexpected url: " + url));
    });

    createWrapper(["/admin/missions/1"], "/admin/missions/:id", "admin");

    await waitFor(() => {
      expect(screen.getByDisplayValue("Test Mission")).toBeInTheDocument();
    });
  });

  it("fetches next order for new mission", async () => {
    const mockSystems = [{ id: "s1", name: "System 1" }];

    mockedApiClient.get.mockImplementation((url: string) => {
      if (url.includes("/star-systems")) return Promise.resolve({ data: mockSystems });
      if (url.includes("/next-order")) return Promise.resolve({ data: 10 });
      return Promise.resolve({ data: {} });
    });

    createWrapper(["/admin/missions/new?starSystemId=s1"], "/admin/missions/new", "admin");

    await waitFor(() => {
      expect(screen.getByDisplayValue("10")).toBeInTheDocument();
    });
  });
});
