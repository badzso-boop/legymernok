import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
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
