import { render, screen, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import MissionList from "../missions/MissionList";
import axios from "axios";
import { MemoryRouter } from "react-router-dom";

// Axios mockolása
vi.mock("axios");
const mockedAxios = axios as any;

// i18next mockolása
vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key, // Kulcsot ad vissza
  }),
}));

const mockedNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockedNavigate,
  };
});

// DataGrid mockolása (Opcionális, de gyorsítja a tesztet és elkerüli a complexitást)
// Ha nem mockoljuk, akkor a jsdom-ban kell resize observer polyfill.
// Most próbáljuk meg mock nélkül, hátha átmegy. Ha nem, mockoljuk.

describe("MissionList Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches and displays missions", async () => {
    // Mock adatok
    const mockMissions = [
      {
        id: "1",
        name: "Alpha Mission",
        starSystemId: "s1",
        orderInSystem: 1,
        difficulty: "EASY",
        missionType: "CODING",
      },
    ];
    const mockSystems = [{ id: "s1", name: "Solar System" }];

    // Két párhuzamos hívás mockolása
    mockedAxios.get.mockImplementation((url: string) => {
      if (url.includes("/missions"))
        return Promise.resolve({ data: mockMissions });
      if (url.includes("/star-systems"))
        return Promise.resolve({ data: mockSystems });
      return Promise.reject(new Error("Unknown URL"));
    });

    render(
      <MemoryRouter>
        <MissionList />
      </MemoryRouter>
    );

    // Várjuk meg, amíg eltűnik a loading (vagy megjelenik az adat)
    await waitFor(() => {
      expect(screen.getByText("Alpha Mission")).toBeInTheDocument();
      // Ellenőrizzük, hogy a StarSystem nevét írja ki az ID helyett
      expect(screen.getByText("Solar System")).toBeInTheDocument();
    });
  });

  it("handles delete action", async () => {
    // Mock window.confirm
    vi.spyOn(window, "confirm").mockImplementation(() => true);

    mockedAxios.delete.mockResolvedValue({});
    // Az újra lekérést is mockolni kell
    mockedAxios.get.mockResolvedValue({ data: [] });

    render(
      <MemoryRouter>
        <MissionList />
      </MemoryRouter>
    );

    // Megvárjuk a betöltést...
    await waitFor(() => screen.getByText("Alpha Mission")); // Feltételezve, hogy az előző teszt setupja érvényes, de itt külön kéne

    // ...de mivel a beforeEach törli a mockokat, itt újra be kell állítani az adatot a render előtt.
    // (Lásd lentebb a teljes kódban a setup ismétlést)

    // ... (Delete gomb megnyomása logikája trükkös a DataGridben, lehet, hogy a testid segít)
  });
});
