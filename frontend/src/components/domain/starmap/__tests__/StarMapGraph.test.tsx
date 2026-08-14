import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ThemeModeProvider } from "../../../../theme/ThemeModeProvider";
import StarMapGraph from "../StarMapGraph";
import type { StarSystemWithProgressResponse } from "../../../../types/starSystem";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const systems: StarSystemWithProgressResponse[] = [
  {
    id: "s1",
    name: "Kezdő rendszer",
    description: null,
    iconUrl: null,
    createdAt: "",
    updatedAt: "",
    status: "NOT_STARTED",
  },
  {
    id: "s2",
    name: "Folyamatban rendszer",
    description: null,
    iconUrl: null,
    createdAt: "",
    updatedAt: "",
    status: "IN_PROGRESS",
  },
  {
    id: "s3",
    name: "Kész rendszer",
    description: null,
    iconUrl: null,
    createdAt: "",
    updatedAt: "",
    status: "COMPLETED",
  },
];

const renderGraph = (props: Partial<React.ComponentProps<typeof StarMapGraph>> = {}) =>
  render(
    <MemoryRouter>
      <ThemeModeProvider>
        <StarMapGraph systems={systems} {...props} />
      </ThemeModeProvider>
    </MemoryRouter>,
  );

describe("StarMapGraph", () => {
  it("hiba nélkül renderel és minden rendszer nevét megjeleníti", () => {
    renderGraph();
    expect(screen.getByText("Kezdő rendszer")).toBeInTheDocument();
    expect(screen.getByText("Folyamatban rendszer")).toBeInTheDocument();
    expect(screen.getByText("Kész rendszer")).toBeInTheDocument();
  });

  it("compact módban nem jeleníti meg a rendszer-neveket", () => {
    renderGraph({ compact: true });
    expect(screen.queryByText("Kezdő rendszer")).not.toBeInTheDocument();
  });

  it("üres lista esetén sem dob hibát", () => {
    const { container } = render(
      <MemoryRouter>
        <ThemeModeProvider>
          <StarMapGraph systems={[]} />
        </ThemeModeProvider>
      </MemoryRouter>,
    );
    expect(container.querySelector('[data-cy="star-map-graph"]')).toBeInTheDocument();
  });
});
