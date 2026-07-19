import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import StarSystemEdit from "../star-system/StarSystemEdit";
import { MemoryRouter, Route, Routes } from "react-router-dom";

// --- Mockok ---

vi.mock("../../../api/client", () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  missionGroupApi: {
    create: vi.fn(),
    getById: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    addMission: vi.fn(),
    removeMission: vi.fn(),
    reorderGroup: vi.fn(),
    reorderMissionInGroup: vi.fn(),
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

import apiClient from "../../../api/client";
const mockedApiClient = apiClient as any;

// --- Tesztek ---

describe("StarSystemEdit Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads existing star system data for editing", async () => {
    // Az új komponens StarSystemWithItemsResponse formátumot vár
    const mockSystemResponse = {
      id: "1",
      name: "Hoth",
      description: "Ice planet",
      iconUrl: "hoth.png",
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      items: [],
    };

    mockedApiClient.get.mockResolvedValue({ data: mockSystemResponse });

    render(
      <MemoryRouter initialEntries={["/admin/star-systems/1"]}>
        <Routes>
          <Route path="/admin/star-systems/:id" element={<StarSystemEdit />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByDisplayValue("Hoth")).toBeInTheDocument();
      expect(screen.getByDisplayValue("Ice planet")).toBeInTheDocument();
    });
  });

  it("creates new star system and navigates back", async () => {
    mockedApiClient.post.mockResolvedValue({ data: {} });

    render(
      <MemoryRouter initialEntries={["/admin/star-systems/new"]}>
        <Routes>
          <Route path="/admin/star-systems/new" element={<StarSystemEdit />} />
        </Routes>
      </MemoryRouter>,
    );

    // Megkeressük a name és description inputokat (label szöveg az i18n kulcs)
    const nameInput = screen.getByLabelText("name");
    const descInput = screen.getByLabelText("description");
    const saveButton = screen.getByText("save");

    fireEvent.change(nameInput, { target: { value: "Naboo" } });
    fireEvent.change(descInput, { target: { value: "Beautiful planet" } });

    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(mockedApiClient.post).toHaveBeenCalledWith(
        "/star-systems",
        { name: "Naboo", description: "Beautiful planet", iconUrl: "" },
      );
    });

    expect(mockedNavigate).toHaveBeenCalledWith("/admin/star-systems");
  });
});
