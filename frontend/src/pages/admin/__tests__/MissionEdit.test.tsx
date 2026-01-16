import { render, screen, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import MissionEdit from "../missions/MissionEdit";
import axios from "axios";
import { MemoryRouter, Route, Routes } from "react-router-dom";

vi.mock("axios");
const mockedAxios = axios as any;

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const mockedNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return { ...actual, useNavigate: () => mockedNavigate };
});

describe("MissionEdit Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads data for editing", async () => {
    const mockMission = {
      id: "1",
      name: "Test Mission",
      starSystemId: "s1",
      orderInSystem: 5,
      difficulty: "HARD",
      missionType: "CODING",
    };
    const mockSystems = [{ id: "s1", name: "System 1" }];

    mockedAxios.get.mockImplementation((url: string) => {
      if (url.includes("/missions/1"))
        return Promise.resolve({ data: mockMission });
      if (url.includes("/star-systems"))
        return Promise.resolve({ data: mockSystems });
      return Promise.reject();
    });

    render(
      <MemoryRouter initialEntries={["/admin/missions/1"]}>
        <Routes>
          <Route path="/admin/missions/:id" element={<MissionEdit />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByDisplayValue("Test Mission")).toBeInTheDocument();
      // A Select értékét nehéz tesztelni, de az inputban ott kell lennie a "System 1"-nek vagy az ID-nak rejtve
    });
  });

  it("fetches next order for new mission", async () => {
    const mockSystems = [{ id: "s1", name: "System 1" }];

    mockedAxios.get.mockImplementation((url: string) => {
      if (url.includes("/star-systems"))
        return Promise.resolve({ data: mockSystems });
      if (url.includes("/next-order")) return Promise.resolve({ data: 10 });
      return Promise.resolve({ data: {} });
    });

    render(
      <MemoryRouter initialEntries={["/admin/missions/new?starSystemId=s1"]}>
        <Routes>
          <Route path="/admin/missions/new" element={<MissionEdit />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      // Ellenőrizzük, hogy a sorszám mezőben megjelenik-e a 10
      expect(screen.getByDisplayValue("10")).toBeInTheDocument();
    });
  });
});
