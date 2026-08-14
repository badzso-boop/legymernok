import { render, screen, fireEvent } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ThemeModeProvider } from "../../theme/ThemeModeProvider";
import LandingPage from "../LandingPage";

// i18n Mockolása: a fordítási kulcsokat adjuk vissza, hogy stabilan tesztelhessünk.
// (LandingPage a router/index-en keresztül tranzitívan importálja a valós i18n
// configot is, ezért itt meg kell tartani az eredeti "initReactI18next" exportot.)
vi.mock("react-i18next", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-i18next")>();
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  };
});

const mockedNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return { ...actual, useNavigate: () => mockedNavigate };
});

let mockIsAuthenticated = false;
vi.mock("../../context/AuthContext", () => ({
  useAuth: () => ({
    isAuthenticated: mockIsAuthenticated,
    hasRole: () => false,
  }),
}));

const renderPage = () =>
  render(
    <ThemeModeProvider>
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>
    </ThemeModeProvider>,
  );

describe("LandingPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockIsAuthenticated = false;
  });

  it("megjeleníti a hero, about, features és FAQ szekciók címét", () => {
    renderPage();

    expect(screen.getByText("landingPage.hero.title")).toBeInTheDocument();
    expect(screen.getByText("landingPage.about.title")).toBeInTheDocument();
    expect(screen.getByText("landingPage.features.title")).toBeInTheDocument();
    expect(screen.getByText("landingPage.faq.title")).toBeInTheDocument();
  });

  it("be nem jelentkezett usernek Regisztráció és Bejelentkezés CTA gombokat mutat, és navigál kattintásra", () => {
    renderPage();

    const registerButton = document.querySelector(
      '[data-cy="hero-cta-register"]',
    ) as HTMLElement;
    expect(registerButton).toBeInTheDocument();

    fireEvent.click(registerButton);
    expect(mockedNavigate).toHaveBeenCalledWith("/register");

    const loginButton = document.querySelector(
      '[data-cy="hero-cta-login"]',
    ) as HTMLElement;
    fireEvent.click(loginButton);
    expect(mockedNavigate).toHaveBeenCalledWith("/login");
  });

  it("bejelentkezett usernek a csillagtérképre mutató CTA-t ad, és nem mutatja a záró CTA szekciót", () => {
    mockIsAuthenticated = true;
    renderPage();

    const continueButton = screen.getByText("landingPage.hero.ctaContinue");
    fireEvent.click(continueButton);
    expect(mockedNavigate).toHaveBeenCalledWith("/star-map");

    expect(
      screen.queryByText("landingPage.finalCta.title"),
    ).not.toBeInTheDocument();
  });

  it("megjeleníti az indítópult (launch console) navigációs gombjait", () => {
    renderPage();

    expect(
      screen.getByText("landingPage.launchConsole.title"),
    ).toBeInTheDocument();
    expect(screen.getByText("controlPanel.starSystems")).toBeInTheDocument();
    expect(screen.getByText("controlPanel.myForge")).toBeInTheDocument();
  });
});
