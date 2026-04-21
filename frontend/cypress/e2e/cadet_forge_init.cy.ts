/// <reference types="cypress" />
import { createMockJwt } from "../support/utils";

describe("Cadet Forge Init (ForgeConfigPanel)", () => {
  const token = createMockJwt(["ROLE_CADET"]);

  const mockSystems = [
    { id: "s1", name: "Solar System", description: "", createdAt: "", updatedAt: "" },
    { id: "s2", name: "Alpha Centauri", description: "", createdAt: "", updatedAt: "" },
  ];

  beforeEach(() => {
    cy.intercept("GET", "**/api/auth/me", {
      statusCode: 200,
      body: { id: "cadet-1", username: "cypress_cadet", roles: ["ROLE_CADET"] },
    }).as("getMe");

    cy.intercept("GET", "**/api/star-systems/my-systems", {
      statusCode: 200,
      body: mockSystems,
    }).as("getMySystems");
  });

  it("should redirect to /login if not authenticated", () => {
    cy.visit("/#/forge");
    cy.url().should("include", "/login");
  });

  it("should render the forge config panel with existing star system", () => {
    cy.visit("/#/forge", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getMySystems");

    cy.get('[data-cy="sector-registry-panel"]').should("exist");
    cy.get('[data-cy="mission-spec-panel"]').should("exist");
    cy.get('[data-cy="forge-submit-btn"]').should("exist");
  });

  it("should initialize a new CODING mission and navigate to the editor", () => {
    cy.intercept("POST", "**/api/missions/forge/initialize", {
      statusCode: 201,
      body: {
        id: "new-m1",
        name: "My Coding Mission",
        missionType: "CODING",
        verificationStatus: "DRAFT",
        starSystemId: "s1",
        orderInSystem: 1,
        difficulty: "EASY",
        createdAt: new Date().toISOString(),
      },
    }).as("initMission");

    cy.visit("/#/forge", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getMySystems");

    cy.get('input[name="name"]').type("My Coding Mission");
    cy.get('[data-cy="forge-submit-btn"]').click();

    cy.wait("@initMission");
    cy.url().should("include", "/forge/new-m1");
  });

  it("should initialize a new QUIZ mission and navigate to the quiz editor", () => {
    cy.intercept("POST", "**/api/missions/forge/initialize", {
      statusCode: 201,
      body: {
        id: "new-q1",
        name: "My Quiz Mission",
        missionType: "QUIZ",
        verificationStatus: "DRAFT",
        starSystemId: "s1",
        orderInSystem: 1,
        difficulty: "EASY",
        createdAt: new Date().toISOString(),
      },
    }).as("initQuizMission");

    cy.visit("/#/forge", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getMySystems");

    // Típus váltás QUIZ-ra
    cy.get("#mui-component-select-missionType").parent().click();
    cy.get('ul[role="listbox"]').contains("KVÍZ").click();

    // Language selector eltűnik QUIZ módban
    cy.get("#mui-component-select-templateLanguage").should("not.exist");

    cy.get('input[name="name"]').type("My Quiz Mission");
    cy.get('[data-cy="forge-submit-btn"]').click();

    cy.wait("@initQuizMission");
    cy.url().should("include", "/forge/new-q1");
  });

  it("should switch to new star system creation mode", () => {
    cy.visit("/#/forge", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getMySystems");

    cy.contains("[+]").click();

    cy.get('input[name="newStarSystemName"]').should("be.visible");
    cy.get("#mui-component-select-starSystemId").should("not.exist");
  });

  it("should create new star system then initialize mission", () => {
    cy.intercept("POST", "**/api/star-systems", {
      statusCode: 201,
      body: { id: "s-new", name: "New Frontier", description: "" },
    }).as("createSystem");

    cy.intercept("POST", "**/api/missions/forge/initialize", {
      statusCode: 201,
      body: {
        id: "m-new",
        name: "Pioneer Mission",
        missionType: "CODING",
        verificationStatus: "DRAFT",
        starSystemId: "s-new",
        orderInSystem: 1,
        difficulty: "EASY",
        createdAt: new Date().toISOString(),
      },
    }).as("initMission");

    cy.intercept("GET", "**/api/star-systems/my-systems", {
      statusCode: 200,
      body: [],
    }).as("getEmptySystems");

    cy.visit("/#/forge", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getEmptySystems");

    // Nincs rendszer → automatikusan new system módban van
    cy.get('input[name="newStarSystemName"]').type("New Frontier");
    cy.get('input[name="name"]').type("Pioneer Mission");
    cy.get('[data-cy="forge-submit-btn"]').click();

    cy.wait("@createSystem");
    cy.wait("@initMission");
    cy.url().should("include", "/forge/m-new");
  });

  it("should show language selector for CODING but hide for QUIZ", () => {
    cy.visit("/#/forge", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getMySystems");

    // Default: CODING → language selector látható
    cy.get("#mui-component-select-templateLanguage").should("exist");

    // QUIZ-ra váltva: eltűnik
    cy.get("#mui-component-select-missionType").parent().click();
    cy.get('ul[role="listbox"]').contains("KVÍZ").click();
    cy.get("#mui-component-select-templateLanguage").should("not.exist");
  });
});
