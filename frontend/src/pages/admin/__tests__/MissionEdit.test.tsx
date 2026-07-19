import { render, screen, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import MissionEdit from "../missions/MissionEdit";
import { MemoryRouter, Route, Routes } from "react-router-dom";

// --- Mockok ---

// apiClient default export + szükséges namespace-ek mock-olása
vi.mock("../../../api/client", () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

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

const mockedNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return { ...actual, useNavigate: () => mockedNavigate };
});

// ContentEditor és FillInBlankEditor mock (tab tartalmak, nem releváns)
vi.mock("../../../components/admin/ContentEditor", () => ({
  default: () => <div data-testid="content-editor-mock" />,
}));
vi.mock("../../../components/admin/FillInBlankEditor", () => ({
  default: () => <div data-testid="fib-editor-mock" />,
}));

import apiClient from "../../../api/client";
const mockedApiClient = apiClient as any;

// --- Tesztek ---

describe("MissionEdit Component", () => {
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
      if (url.includes("/missions/1"))
        return Promise.resolve({ data: mockMission });
      if (url.includes("/star-systems"))
        return Promise.resolve({ data: mockSystems });
      return Promise.reject(new Error("unexpected url: " + url));
    });

    render(
      <MemoryRouter initialEntries={["/admin/missions/1"]}>
        <Routes>
          <Route path="/admin/missions/:id" element={<MissionEdit />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByDisplayValue("Test Mission")).toBeInTheDocument();
    });
  });

  it("fetches next order for new mission", async () => {
    const mockSystems = [{ id: "s1", name: "System 1" }];

    mockedApiClient.get.mockImplementation((url: string) => {
      if (url.includes("/star-systems"))
        return Promise.resolve({ data: mockSystems });
      if (url.includes("/next-order"))
        return Promise.resolve({ data: 10 });
      return Promise.resolve({ data: {} });
    });

    render(
      <MemoryRouter initialEntries={["/admin/missions/new?starSystemId=s1"]}>
        <Routes>
          <Route path="/admin/missions/new" element={<MissionEdit />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByDisplayValue("10")).toBeInTheDocument();
    });
  });
});
