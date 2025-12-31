import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import StarSystemEdit from "../star-system/StarSystemEdit";
import axios from "axios";
import { MemoryRouter, Route, Routes } from "react-router-dom";

// Axios mockolása
vi.mock("axios");
const mockedAxios = axios as any;

// i18next mockolása
vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
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

describe("StarSystemEdit Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads existing star system data for editing", async () => {
    const mockSystem = {
      id: "1",
      name: "Hoth",
      description: "Ice planet",
      iconUrl: "hoth.png",
      missions: [],
    };

    mockedAxios.get.mockResolvedValue({ data: mockSystem });

    render(
      <MemoryRouter initialEntries={["/admin/star-systems/1"]}>
        <Routes>
          <Route path="/admin/star-systems/:id" element={<StarSystemEdit />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByDisplayValue("Hoth")).toBeInTheDocument();
      expect(screen.getByDisplayValue("Ice planet")).toBeInTheDocument();
    });
  });

  it("creates new star system", async () => {
    mockedAxios.post.mockResolvedValue({});

    render(
      <MemoryRouter initialEntries={["/admin/star-systems/new"]}>
        <Routes>
          <Route path="/admin/star-systems/new" element={<StarSystemEdit />} />
        </Routes>
      </MemoryRouter>
    );

    const nameInput = screen.getByLabelText("name");
    const descInput = screen.getByLabelText("description");
    const saveButton = screen.getByText("save");

    fireEvent.change(nameInput, { target: { value: "Naboo" } });
    fireEvent.change(descInput, { target: { value: "Beautiful planet" } });

    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(mockedAxios.post).toHaveBeenCalledWith(
        expect.stringContaining("/star-systems"),
        {
          name: "Naboo",
          description: "Beautiful planet",
          iconUrl: "",
        },
        expect.any(Object)
      );
    });

    expect(mockedNavigate).toHaveBeenCalledWith("/admin/star-systems");
  });
});
