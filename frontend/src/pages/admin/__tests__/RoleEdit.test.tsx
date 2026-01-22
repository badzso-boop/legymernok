import { render, screen, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import RoleEdit from "../roles/RoleEdit";
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

describe("RoleEdit Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads data for editing", async () => {
    const mockRole = {
      id: "1",
      name: "ROLE_EDIT",
      description: "Desc",
      permissions: [],
    };
    const mockPermissions = [
      { id: "p1", name: "perm:read", description: "Read" },
    ];

    mockedAxios.get.mockImplementation((url: string) => {
      if (url.includes("/permissions"))
        return Promise.resolve({ data: mockPermissions });
      if (url.includes("/roles/1")) return Promise.resolve({ data: mockRole });
      return Promise.reject();
    });

    render(
      <MemoryRouter initialEntries={["/admin/roles/1"]}>
        <Routes>
          <Route path="/admin/roles/:id" element={<RoleEdit />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByDisplayValue("ROLE_EDIT")).toBeInTheDocument();
      expect(screen.getByText("perm:read")).toBeInTheDocument();
    });
  });
});
