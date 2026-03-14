/// <reference types="cypress" />
import { createMockJwt } from "../support/utils";

describe("Cadet Quiz Player Page", () => {
  const token = createMockJwt(["ROLE_CADET"]);
  const missionId = "quiz-m1";

  const mockQuiz = {
    config: {
      timeLimitSeconds: 300,
      allowNavigation: true,
      showSolutions: false,
    },
    questions: [
      {
        id: "q1",
        text: "Mi a 2+2 eredménye?",
        points: 10,
        options: [
          { id: "o1", text: "3" },
          { id: "o2", text: "4" },
          { id: "o3", text: "5" },
        ],
      },
      {
        id: "q2",
        text: "Mi a főváros?",
        points: 10,
        options: [
          { id: "o4", text: "Bécs" },
          { id: "o5", text: "Budapest" },
          { id: "o6", text: "Prága" },
        ],
      },
    ],
  };

  const mockResult = {
    id: "result-1",
    score: 8,
    maxScore: 10,
    percentage: 80,
    isLate: false,
    completedAt: new Date().toISOString(),
    detailedAnswers: "{}",
  };

  beforeEach(() => {
    cy.intercept("GET", "**/api/auth/me", {
      statusCode: 200,
      body: { id: "cadet-1", username: "cypress_cadet", roles: ["ROLE_CADET"] },
    }).as("getMe");

    cy.intercept("POST", `**/api/quiz/${missionId}/start`, {
      statusCode: 200,
      body: mockQuiz,
    }).as("startQuiz");
  });

  it("should redirect to /login if not authenticated", () => {
    cy.visit(`/#/play/quiz/${missionId}`);
    cy.url().should("include", "/login");
  });

  it("should show loading then render quiz questions", () => {
    cy.visit(`/#/play/quiz/${missionId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@startQuiz");

    cy.contains("Mi a 2+2 eredménye?").should("be.visible");
    cy.contains("3").should("be.visible");
    cy.contains("4").should("be.visible");
    cy.contains("5").should("be.visible");
  });

  it("should show error state when quiz fails to load", () => {
    cy.intercept("POST", `**/api/quiz/${missionId}/start`, {
      statusCode: 500,
      body: { message: "Server error" },
    }).as("startQuizError");

    cy.visit(`/#/play/quiz/${missionId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@startQuizError");

    cy.contains("ERROR_LOADING_MISSION_DATA").should("be.visible");
  });

  it("should navigate between questions with Next and Previous buttons", () => {
    cy.visit(`/#/play/quiz/${missionId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@startQuiz");

    // Az első kérdésnél a Previous disabled
    cy.contains("VISSZA").should("be.disabled");

    // Next gombra kattintás
    cy.contains("TOVÁBB").click();
    cy.contains("Mi a főváros?").should("be.visible");

    // Visszalépés
    cy.contains("VISSZA").click();
    cy.contains("Mi a 2+2 eredménye?").should("be.visible");
  });

  it("should show Finish button on the last question", () => {
    cy.visit(`/#/play/quiz/${missionId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@startQuiz");

    // Az első kérdésnél NEXT gomb van
    cy.contains("BEFEJEZÉS").should("not.exist");
    cy.contains("TOVÁBB").should("be.visible");

    // Az utolsó kérdésnél BEFEJEZÉS gomb van
    cy.contains("TOVÁBB").click();
    cy.contains("BEFEJEZÉS").should("be.visible");
    cy.contains("TOVÁBB").should("not.exist");
  });

  it("should select a single-choice answer and submit the quiz", () => {
    cy.intercept("POST", `**/api/quiz/${missionId}/submit`, {
      statusCode: 200,
      body: mockResult,
    }).as("submitQuiz");

    cy.visit(`/#/play/quiz/${missionId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@startQuiz");

    // Válasz kijelölés az első kérdésnél
    cy.contains("4").click();

    // Tovább a második kérdéshez, ott is választunk
    cy.contains("TOVÁBB").click();
    cy.contains("Budapest").click();

    // Beküldés
    cy.contains("BEFEJEZÉS").click();
    cy.wait("@submitQuiz");

    // Eredmény képernyő megjelenik
    cy.get('[data-cy="quiz-result"]').should("be.visible");
    cy.contains("80").should("be.visible");
    cy.contains("MISSION_ACCOMPLISHED").should("be.visible");
  });

  it("should handle 409 conflict (already submitted) and show previous result", () => {
    cy.intercept("POST", `**/api/quiz/${missionId}/submit`, {
      statusCode: 409,
      body: {
        message: "Already submitted",
        data: {
          id: "result-old",
          score: 6,
          maxScore: 10,
          percentage: 60,
          isLate: false,
          completedAt: new Date().toISOString(),
          detailedAnswers: "{}",
        },
      },
    }).as("submitQuizConflict");

    cy.visit(`/#/play/quiz/${missionId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@startQuiz");

    // Navigálj az utolsó kérdéshez és küldd be
    cy.contains("TOVÁBB").click();
    cy.contains("BEFEJEZÉS").click();
    cy.wait("@submitQuizConflict");

    // A korábbi eredmény jelenik meg
    cy.get('[data-cy="quiz-result"]').should("be.visible");
    cy.contains("60").should("be.visible");
  });

  it("should auto-submit when the timer expires", () => {
    const shortTimerQuiz = {
      ...mockQuiz,
      config: { ...mockQuiz.config, timeLimitSeconds: 2 },
    };

    cy.intercept("POST", `**/api/quiz/${missionId}/start`, {
      statusCode: 200,
      body: shortTimerQuiz,
    }).as("startShortQuiz");

    cy.intercept("POST", `**/api/quiz/${missionId}/submit`, {
      statusCode: 200,
      body: mockResult,
    }).as("submitQuiz");

    // cy.clock() ELŐBB kell lennie a cy.visit()-nél
    cy.clock();

    cy.visit(`/#/play/quiz/${missionId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@startShortQuiz");

    // Megvárjuk, hogy a QuizPlayer tényleg renderelt (React state setup)
    cy.contains("Mi a 2+2 eredménye?");

    // Timer elindul: 2 másodperc → 2500ms tick elegendő
    cy.tick(2500);

    cy.wait("@submitQuiz");
    cy.get('[data-cy="quiz-result"]').should("be.visible");
  });
});
