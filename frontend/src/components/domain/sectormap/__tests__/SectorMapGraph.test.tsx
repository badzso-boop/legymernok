import React from "react";
import { render, screen, fireEvent, act } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { ThemeModeProvider } from "../../../../theme/ThemeModeProvider";
import SectorMapGraph from "../SectorMapGraph";
import type { SectorResponse } from "../../../../types/sector";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const sectors: SectorResponse[] = [
  {
    id: "sec1",
    name: "Fizika",
    description: null,
    iconUrl: null,
    orderIndex: 0,
    starSystemCount: 3,
    createdAt: "",
    updatedAt: "",
  },
  {
    id: "sec2",
    name: "Informatika",
    description: null,
    iconUrl: null,
    orderIndex: 1,
    starSystemCount: 5,
    createdAt: "",
    updatedAt: "",
  },
];

const renderGraph = (props: Partial<React.ComponentProps<typeof SectorMapGraph>> = {}) =>
  render(
    <MemoryRouter>
      <ThemeModeProvider>
        <SectorMapGraph sectors={sectors} unassignedCount={0} {...props} />
      </ThemeModeProvider>
    </MemoryRouter>,
  );

describe("SectorMapGraph", () => {
  it("hiba nélkül renderel és minden szektor nevét megjeleníti", () => {
    renderGraph();
    expect(screen.getByText("Fizika")).toBeInTheDocument();
    expect(screen.getByText("Informatika")).toBeInTheDocument();
  });

  it("megjeleníti a Besorolatlan node-ot, ha van besorolatlan rendszer", () => {
    renderGraph({ unassignedCount: 2 });
    expect(screen.getByText("sectorMap.unassigned")).toBeInTheDocument();
  });

  it("nem jeleníti meg a Besorolatlan node-ot, ha nincs besorolatlan rendszer", () => {
    renderGraph({ unassignedCount: 0 });
    expect(screen.queryByText("sectorMap.unassigned")).not.toBeInTheDocument();
  });

  it("node kattintás után (warp-animáció letelte) navigál a /star-map/:sectorId-ra", () => {
    vi.useFakeTimers();
    render(
      <MemoryRouter initialEntries={["/sector-map"]}>
        <ThemeModeProvider>
          <Routes>
            <Route
              path="/sector-map"
              element={<SectorMapGraph sectors={sectors} unassignedCount={0} />}
            />
            <Route path="/star-map/:sectorId" element={<div>star-map-page</div>} />
          </Routes>
        </ThemeModeProvider>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByText("Fizika"));
    act(() => {
      vi.advanceTimersByTime(600);
    });

    expect(screen.getByText("star-map-page")).toBeInTheDocument();
    vi.useRealTimers();
  });

  it("üres lista esetén sem dob hibát", () => {
    const { container } = render(
      <MemoryRouter>
        <ThemeModeProvider>
          <SectorMapGraph sectors={[]} unassignedCount={0} />
        </ThemeModeProvider>
      </MemoryRouter>,
    );
    expect(container.querySelector('[data-cy="sector-map-graph"]')).toBeInTheDocument();
  });
});
