/// <reference types="cypress" />
import { createMockJwt } from "../support/utils";

describe("User Group Player Flow (Mocked Backend)", () => {
  const token = createMockJwt(["ROLE_CADET"]);

  // --- Közös mock adatok ---

  const groupId = "group-1";
  const starSystemId = "system-1";

  const mockGroup = {
    id: groupId,
    name: "Bevezető Csoport",
    description: null,
    starSystemId,
    starSystemName: "Tatooine",
    orderIndex: 1,
    missions: [
      {
        id: "content-m1",
        name: "Elmélet",
        missionType: "CONTENT",
        difficulty: "EASY",
        starSystemId,
        descriptionMarkdown: "",
        templateRepositoryUrl: null,
        orderIndex: 1,
        groupId,
        groupOrder: 1,
        createdAt: "2024-01-01T10:00:00Z",
      },
      {
        id: "fib-m1",
        name: "Kitöltős",
        missionType: "FILL_IN_BLANK",
        difficulty: "EASY",
        starSystemId,
        descriptionMarkdown: "",
        templateRepositoryUrl: null,
        orderIndex: 2,
        groupId,
        groupOrder: 2,
        createdAt: "2024-01-01T10:00:00Z",
      },
      {
        id: "quiz-m1",
        name: "Kvíz",
        missionType: "QUIZ",
        difficulty: "MEDIUM",
        starSystemId,
        descriptionMarkdown: "",
        templateRepositoryUrl: null,
        orderIndex: 3,
        groupId,
        groupOrder: 3,
        createdAt: "2024-01-01T10:00:00Z",
      },
    ],
  };

  const makeProgress = (nextMissionId: string | null, completed = false, completedCount = 0) => ({
    groupId,
    completed,
    nextMissionId,
    completedCount,
    totalCount: 3,
    startedAt: "2024-01-01T10:00:00Z",
    lastUpdatedAt: "2024-01-01T10:00:00Z",
    completedAt: completed ? "2024-01-01T11:00:00Z" : null,
  });

  const mockContentPage = {
    missionId: "content-m1",
    missionName: "Elmélet",
    content: "# Bevezetés\n\nEz az első fejezet tartalma.",
    page: 0,
    pageSize: 100,
    totalLines: 5,
    totalPages: 1,
    hasNextPage: false,
    hasPreviousPage: false,
  };

  const mockFibDefinition = {
    missionId: "fib-m1",
    templateText: "A víz forráspontja [[blank_1]] Celsius.",
    passThreshold: 50,
    blanks: [
      {
        id: "b1",
        key: "blank_1",
        orderIndex: 0,
        options: [
          { id: "opt1", optionText: "100", orderIndex: 0 },
          { id: "opt2", optionText: "50", orderIndex: 1 },
        ],
      },
    ],
  };

  const mockQuiz = {
    config: { timeLimitSeconds: 300, allowNavigation: true, showSolutions: false },
    questions: [
      {
        id: "q1",
        text: "Mi a 2+2?",
        points: 10,
        options: [
          { id: "o1", text: "3" },
          { id: "o2", text: "4" },
        ],
      },
    ],
  };

  const mockStarSystemWithGroup = {
    id: starSystemId,
    name: "Tatooine",
    description: "Desert planet",
    iconUrl: "",
    createdAt: "2024-01-01T10:00:00Z",
    updatedAt: "2024-01-01T10:00:00Z",
    items: [
      {
        type: "GROUP",
        orderIndex: 1,
        group: {
          id: groupId,
          name: "Bevezető Csoport",
          description: null,
          starSystemId,
          orderIndex: 1,
          createdAt: "2024-01-01T10:00:00Z",
          updatedAt: "2024-01-01T10:00:00Z",
        },
        groupMissions: mockGroup.missions,
      },
    ],
  };

  beforeEach(() => {
    cy.intercept("GET", "**/api/auth/me", {
      statusCode: 200,
      body: { username: "cypress_cadet", roles: ["ROLE_CADET"] },
    }).as("getMe");
  });

  // ─────────────────────────────────────────────
  // 1. Teljes flow: CONTENT → FIB → QUIZ → befejezés → ✓ badge
  // ─────────────────────────────────────────────
  it("should complete full group flow: CONTENT → FIB → QUIZ → completion badge", () => {
    cy.intercept("GET", `**/api/star-systems/${starSystemId}/with-missions`, {
      statusCode: 200,
      body: mockStarSystemWithGroup,
    }).as("getStarSystem");

    // ── VISIT előtt regisztrálva: GroupPlayer szinkron mountolódik HashRouter navigációkor ──
    cy.intercept("GET", `**/api/mission-groups/${groupId}`, {
      statusCode: 200,
      body: mockGroup,
    }).as("getGroup");

    cy.intercept("POST", `**/api/group-progress/${groupId}/start`, {
      statusCode: 201,
      body: makeProgress("content-m1"),
    }).as("startProgress");

    // LIFO: getProgressStep1 előbb → getProgressNotStarted utóbb (utolsó = első illeszkedés)
    // Eredmény: első GET 404 (not started), utána 200 (content-m1 progress)
    cy.intercept("GET", `**/api/group-progress/${groupId}`, {
      statusCode: 200,
      body: makeProgress("content-m1"),
    }).as("getProgressStep1");

    cy.intercept("GET", `**/api/missions/content-m1/content*`, {
      statusCode: 200,
      body: mockContentPage,
    }).as("getContent");

    // FIB: FillInBlankView szinkron mountolódik amint setProgress(fib progress) lefut
    cy.intercept("GET", `**/api/missions/fib-m1/fill-in-blank`, {
      statusCode: 200,
      body: mockFibDefinition,
    }).as("getFib");

    cy.intercept("GET", `**/api/missions/fib-m1/fill-in-blank/last-attempt`, {
      statusCode: 404,
      body: {},
    }).as("getLastAttempt");

    // Quiz: QuizPlayerComponent szinkron mountolódik amint setProgress(quiz progress) lefut
    cy.intercept("POST", `**/api/quiz/quiz-m1/start`, {
      statusCode: 200,
      body: mockQuiz,
    }).as("startQuiz");

    // LIFO utolsó → első group-progress GET kapja el (404 = nem indult még)
    cy.intercept("GET", `**/api/group-progress/${groupId}`, {
      statusCode: 404,
      body: {},
      times: 1,
    }).as("getProgressNotStarted");

    cy.visit(`/#/star-systems/${starSystemId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getStarSystem");

    // completeStep1: "KÖVETKEZŐ" (content→fib) kattintás előtt kell
    cy.intercept("POST", `**/api/group-progress/${groupId}/complete-step`, {
      statusCode: 200,
      body: makeProgress("fib-m1", false, 1),
      times: 1,
    }).as("completeStep1");

    // "KEZDD EL" gomb
    cy.contains("KEZDD EL").closest(".button-group").find("button").click();

    cy.url().should("include", `/play/group/${groupId}`);
    cy.wait("@getGroup");
    cy.wait("@startProgress");
    cy.wait("@getProgressStep1");
    cy.wait("@getContent");

    cy.contains("Bevezetés").should("be.visible");
    cy.contains("1 / 3").should("be.visible");

    // ── CONTENT → FIB ──
    cy.contains("KÖVETKEZŐ").closest(".button-group").find("button").click({ force: true });
    cy.wait("@completeStep1");
    cy.wait("@getFib");
    cy.wait("@getLastAttempt");

    cy.contains("2 / 3").should("be.visible");

    cy.contains("100").click();

    cy.intercept("POST", `**/api/missions/fib-m1/fill-in-blank/submit`, {
      statusCode: 200,
      body: {
        score: 1,
        maxScore: 1,
        percentage: 100,
        passed: true,
        submittedAt: new Date().toISOString(),
        details: [{ blankKey: "blank_1", selectedOptionId: "opt1", correct: true, correctOptionTexts: ["100"] }],
      },
    }).as("submitFib");

    cy.contains("BEKÜLDÉS").closest(".button-group").find("button").click({ force: true });
    cy.wait("@submitFib");

    cy.contains("TELJESÍTVE").should("be.visible");

    // completeStep2: TELJESÍTVE utáni KÖVETKEZŐ (fib→quiz) kattintás előtt
    // (startQuiz QuizPlayerComponent mountjára szükséges, ami completeStep2 után azonnal jön)
    cy.intercept("POST", `**/api/group-progress/${groupId}/complete-step`, {
      statusCode: 200,
      body: makeProgress("quiz-m1", false, 2),
      times: 1,
    }).as("completeStep2");

    // ── FIB → QUIZ ──
    cy.contains("KÖVETKEZŐ").closest(".button-group").find("button").click({ force: true });
    cy.wait("@completeStep2");
    cy.wait("@startQuiz");

    cy.contains("Mi a 2+2?").should("be.visible");
    cy.contains("3 / 3").should("be.visible");

    cy.intercept("POST", `**/api/quiz/quiz-m1/submit`, {
      statusCode: 200,
      body: { id: "r1", score: 10, maxScore: 10, percentage: 100, isLate: false, completedAt: new Date().toISOString(), detailedAnswers: "{}" },
    }).as("submitQuiz");

    cy.intercept("POST", `**/api/group-progress/${groupId}/complete-step`, {
      statusCode: 200,
      body: makeProgress(null, true, 3),
    }).as("completeStep3");

    cy.get('[data-cy="quiz-finish-btn"]').click({ force: true });
    cy.wait("@submitQuiz");
    cy.wait("@completeStep3");

    // ── Befejezési képernyő ──
    cy.contains("CSOPORT TELJESÍTVE").should("be.visible");
    cy.contains("BEVEZETŐ CSOPORT").should("be.visible");

    cy.intercept("GET", `**/api/star-systems/${starSystemId}/with-missions`, {
      statusCode: 200,
      body: mockStarSystemWithGroup,
    }).as("getStarSystemAgain");

    cy.intercept("GET", `**/api/group-progress/${groupId}`, {
      statusCode: 200,
      body: makeProgress(null, true, 3),
    }).as("getProgressCompleted");

    cy.contains("VISSZA A RENDSZERHEZ").closest(".button-group").find("button").click({ force: true });

    cy.url().should("include", `/star-systems/${starSystemId}`);
    cy.wait("@getStarSystemAgain");
    cy.wait("@getProgressCompleted");

    cy.contains("✓ KÉSZ").should("be.visible");
  });

  // ─────────────────────────────────────────────
  // 2. Folytatás — 1. lépés után vissza → "FOLYTATÁS" gomb
  // ─────────────────────────────────────────────
  it("should show CONTINUE button after partial progress", () => {
    cy.intercept("GET", `**/api/star-systems/${starSystemId}/with-missions`, {
      statusCode: 200,
      body: mockStarSystemWithGroup,
    }).as("getStarSystem");

    cy.intercept("GET", `**/api/group-progress/${groupId}`, {
      statusCode: 200,
      body: makeProgress("fib-m1", false, 1),
    }).as("getProgressInProgress");

    cy.visit(`/#/star-systems/${starSystemId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getStarSystem");
    cy.wait("@getProgressInProgress");

    // "FOLYTATÁS" gomb és "1 / 3" chip látható
    cy.contains("FOLYTATÁS").should("be.visible");
    cy.contains("1 / 3").should("be.visible");

    // Kattintás → GroupPlayer a 2. lépésnél nyílik
    cy.intercept("GET", `**/api/mission-groups/${groupId}`, {
      statusCode: 200,
      body: mockGroup,
    }).as("getGroup");

    cy.intercept("GET", `**/api/group-progress/${groupId}`, {
      statusCode: 200,
      body: makeProgress("fib-m1", false, 1),
    }).as("getProgressFib");

    cy.contains("FOLYTATÁS").closest(".button-group").find("button").click({ force: true });

    cy.url().should("include", `/play/group/${groupId}`);
    cy.wait("@getGroup");
    cy.wait("@getProgressFib");

    // FIB betölt (nem a CONTENT)
    cy.intercept("GET", `**/api/fill-in-blank/fib-m1`, {
      statusCode: 200,
      body: mockFibDefinition,
    }).as("getFib");

    cy.intercept("GET", `**/api/fill-in-blank/fib-m1/last-attempt`, {
      statusCode: 404,
      body: {},
    }).as("getLastAttempt");

    cy.wait("@getFib");
    cy.wait("@getLastAttempt");

    // 2 / 3 lépésnél vagyunk
    cy.contains("2 / 3").should("be.visible");
  });

  // ─────────────────────────────────────────────
  // 3. FIB visszatérés — "Már teljesítetted" banner
  // ─────────────────────────────────────────────
  it("should show already-passed banner when returning to completed FIB", () => {
    cy.intercept("GET", `**/api/mission-groups/${groupId}`, {
      statusCode: 200,
      body: mockGroup,
    }).as("getGroup");

    cy.intercept("GET", `**/api/group-progress/${groupId}`, {
      statusCode: 200,
      body: makeProgress("fib-m1", false, 1),
    }).as("getProgress");

    cy.intercept("GET", `**/api/fill-in-blank/fib-m1`, {
      statusCode: 200,
      body: mockFibDefinition,
    }).as("getFib");

    cy.intercept("GET", `**/api/fill-in-blank/fib-m1/last-attempt`, {
      statusCode: 200,
      body: {
        passed: true,
        percentage: 100,
        submittedAt: new Date().toISOString(),
      },
    }).as("getLastAttemptPassed");

    cy.visit(`/#/play/group/${groupId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getGroup");
    cy.wait("@getProgress");
    cy.wait("@getFib");
    cy.wait("@getLastAttemptPassed");

    // "Már teljesítetted" banner megjelenik
    cy.contains("Már teljesítetted").should("be.visible");

    // "KÖVETKEZŐ" gomb azonnal elérhető (nem kell újra kitölteni)
    cy.contains("KÖVETKEZŐ").should("be.visible");
  });
});
