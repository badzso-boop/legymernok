import { act, render, screen } from "@testing-library/react";
import { renderHook } from "@testing-library/react";
import { describe, it, expect, beforeEach } from "vitest";
import { ThemeModeProvider, useThemeMode } from "../ThemeModeProvider";

function Probe() {
  const { mode, setMode } = useThemeMode();
  return (
    <div>
      <span data-testid="mode">{mode}</span>
      <button onClick={() => setMode("dark")}>set-dark</button>
    </div>
  );
}

describe("ThemeModeProvider", () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute("data-theme");
  });

  it("defaults to 'space' when nothing is stored", () => {
    render(
      <ThemeModeProvider>
        <Probe />
      </ThemeModeProvider>,
    );
    expect(screen.getByTestId("mode").textContent).toBe("space");
    expect(document.documentElement.getAttribute("data-theme")).toBe("space");
  });

  it("initializes from a previously stored preference", () => {
    localStorage.setItem("theme-preference", "light");
    render(
      <ThemeModeProvider>
        <Probe />
      </ThemeModeProvider>,
    );
    expect(screen.getByTestId("mode").textContent).toBe("light");
  });

  it("updates data-theme, localStorage and CSS variables on setMode", () => {
    render(
      <ThemeModeProvider>
        <Probe />
      </ThemeModeProvider>,
    );

    act(() => {
      screen.getByText("set-dark").click();
    });

    expect(screen.getByTestId("mode").textContent).toBe("dark");
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    expect(localStorage.getItem("theme-preference")).toBe("dark");
    expect(
      document.documentElement.style.getPropertyValue("--color-bg-base"),
    ).not.toBe("");
  });

  it("throws when useThemeMode is used outside the provider", () => {
    const { result } = renderHook(() => {
      try {
        useThemeMode();
        return null;
      } catch (e) {
        return e as Error;
      }
    });
    expect(result.current).toBeInstanceOf(Error);
  });
});
