/// <reference types="cypress" />
import { createMockJwt } from "../support/utils";

const token = createMockJwt(["ROLE_ADMIN"]);

const mockDefinition = {
  id: "def-1",
  missionId: "mission-1",
  boardType: "ARDUINO_UNO",
  status: "IN_WORK",
  components: [
    {
      id: "comp-1",
      componentType: "LED",
      label: "LED1",
      posX: 100,
      posY: 200,
      properties: [],
    },
  ],
  connections: [],
  checks: [],
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
};

const publishedDefinition = { ...mockDefinition, status: "PUBLISHED" };

const mockCheck = {
  id: "check-1",
  checkType: "CIRCUIT_TOPOLOGY",
  severity: "ERROR",
  i18nKey: "circuit.check.led_connected",
  orderIndex: 0,
};

const mockMe = {
  id: "admin-id",
  username: "cypress_admin",
  email: "admin@test.com",
  fullName: "Cypress Admin",
  roles: ["ROLE_ADMIN"],
};

const visitForge = (missionId = "mission-1") => {
  cy.visit(`/#/admin/circuit/${missionId}`, {
    onBeforeLoad(win) {
      win.localStorage.setItem("token", token);
    },
  });
};

const interceptMe = () =>
  cy.intercept("GET", "**/api/auth/me", { statusCode: 200, body: mockMe }).as("getMe");

describe("Admin Circuit Forge", () => {
  // ---------------------------------------------------------------------------
  // 1. Empty state — no definition
  // ---------------------------------------------------------------------------
  it("shows create definition UI when no definition exists", () => {
    interceptMe();
    cy.intercept("GET", "**/api/circuit/definitions/by-mission/**", {
      statusCode: 200,
      body: [],
    }).as("getDefs");

    visitForge();
    cy.wait("@getDefs");
    cy.get("[data-cy='create-definition-btn']").should("be.visible");
  });

  // ---------------------------------------------------------------------------
  // 2. Create new definition
  // ---------------------------------------------------------------------------
  it("creates a new definition and shows the canvas", () => {
    interceptMe();
    cy.intercept("GET", "**/api/circuit/definitions/by-mission/**", {
      statusCode: 200,
      body: [],
    }).as("getDefs");
    cy.intercept("POST", "**/api/circuit/definitions", {
      statusCode: 201,
      body: mockDefinition,
    }).as("createDef");

    visitForge();
    cy.wait("@getDefs");
    cy.get("[data-cy='create-definition-btn']").click();
    cy.wait("@createDef");
    cy.get("[data-cy='circuit-canvas']").should("exist");
  });

  // ---------------------------------------------------------------------------
  // 3. Load existing definition
  // ---------------------------------------------------------------------------
  it("loads existing definition and shows status chip", () => {
    interceptMe();
    cy.intercept("GET", "**/api/circuit/definitions/by-mission/**", {
      statusCode: 200,
      body: [mockDefinition],
    }).as("getDefs");

    visitForge();
    cy.wait("@getDefs");
    cy.contains("IN_WORK").should("be.visible");
    cy.get("[data-cy='save-canvas-btn']").should("be.visible");
  });

  // ---------------------------------------------------------------------------
  // 4. Save canvas
  // ---------------------------------------------------------------------------
  it("calls PUT /canvas on save and removes unsaved chip", () => {
    interceptMe();
    cy.intercept("GET", "**/api/circuit/definitions/by-mission/**", {
      statusCode: 200,
      body: [mockDefinition],
    }).as("getDefs");
    cy.intercept("PUT", "**/api/circuit/definitions/*/canvas", {
      statusCode: 200,
      body: mockDefinition,
    }).as("saveCanvas");

    visitForge();
    cy.wait("@getDefs");
    cy.get("[data-cy='save-canvas-btn']").click();
    cy.wait("@saveCanvas");
    cy.get("[data-cy='unsaved-changes-chip']").should("not.exist");
  });

  // ---------------------------------------------------------------------------
  // 5. Add verification check
  // ---------------------------------------------------------------------------
  it("adds a verification check and displays it", () => {
    interceptMe();
    cy.intercept("GET", "**/api/circuit/definitions/by-mission/**", {
      statusCode: 200,
      body: [mockDefinition],
    }).as("getDefs");
    cy.intercept("POST", "**/api/circuit/definitions/*/checks", {
      statusCode: 201,
      body: mockCheck,
    }).as("addCheck");

    visitForge();
    cy.wait("@getDefs");

    cy.get("[data-cy='i18n-key-input'] input").type("circuit.check.led_connected");
    cy.get("[data-cy='add-check-btn']").click();
    cy.wait("@addCheck");
    cy.contains("circuit.check.led_connected").should("be.visible");
  });

  // ---------------------------------------------------------------------------
  // 6. Delete verification check
  // ---------------------------------------------------------------------------
  it("deletes a verification check after confirmation", () => {
    const defWithCheck = { ...mockDefinition, checks: [mockCheck] };
    interceptMe();
    cy.intercept("GET", "**/api/circuit/definitions/by-mission/**", {
      statusCode: 200,
      body: [defWithCheck],
    }).as("getDefs");
    cy.intercept("DELETE", "**/api/circuit/definitions/*/checks/*", {
      statusCode: 204,
    }).as("deleteCheck");

    cy.on("window:confirm", () => true);

    visitForge();
    cy.wait("@getDefs");
    cy.get(`[data-cy='delete-check-btn-${mockCheck.id}']`).click();
    cy.wait("@deleteCheck");
    cy.contains("circuit.check.led_connected").should("not.exist");
  });

  // ---------------------------------------------------------------------------
  // 7. Publish definition
  // ---------------------------------------------------------------------------
  it("publishes definition and shows PUBLISHED status chip", () => {
    interceptMe();
    cy.intercept("GET", "**/api/circuit/definitions/by-mission/**", {
      statusCode: 200,
      body: [mockDefinition],
    }).as("getDefs");
    cy.intercept("POST", "**/api/circuit/definitions/*/publish", {
      statusCode: 200,
      body: publishedDefinition,
    }).as("publish");

    visitForge();
    cy.wait("@getDefs");
    cy.contains("circuit.publish").click();
    cy.wait("@publish");
    cy.contains("PUBLISHED").should("be.visible");
  });

  // ---------------------------------------------------------------------------
  // 8. Unpublish definition
  // ---------------------------------------------------------------------------
  it("unpublishes a published definition and shows IN_WORK status", () => {
    interceptMe();
    cy.intercept("GET", "**/api/circuit/definitions/by-mission/**", {
      statusCode: 200,
      body: [publishedDefinition],
    }).as("getDefs");
    cy.intercept("POST", "**/api/circuit/definitions/*/unpublish", {
      statusCode: 200,
      body: mockDefinition,
    }).as("unpublish");

    visitForge();
    cy.wait("@getDefs");
    cy.contains("circuit.unpublish").click();
    cy.wait("@unpublish");
    cy.contains("IN_WORK").should("be.visible");
  });

  // ---------------------------------------------------------------------------
  // 9. Back navigation
  // ---------------------------------------------------------------------------
  it("navigates back to missions on back button click", () => {
    interceptMe();
    cy.intercept("GET", "**/api/circuit/definitions/by-mission/**", {
      statusCode: 200,
      body: [mockDefinition],
    }).as("getDefs");

    visitForge("mission-1");
    cy.wait("@getDefs");
    cy.contains("nav.back").click();
    cy.url().should("include", "/admin/missions/mission-1");
  });

  // ---------------------------------------------------------------------------
  // 10. Error state
  // ---------------------------------------------------------------------------
  it("shows error alert when API returns 500", () => {
    interceptMe();
    cy.intercept("GET", "**/api/circuit/definitions/by-mission/**", {
      statusCode: 500,
    }).as("getDefs");

    visitForge();
    cy.wait("@getDefs");
    cy.contains("circuit.errorLoad").should("be.visible");
  });

  // ---------------------------------------------------------------------------
  // 11. Circuit Forge button visible on CIRCUIT_SIMULATION mission
  // ---------------------------------------------------------------------------
  it("shows Circuit Forge button on CIRCUIT_SIMULATION mission in MissionEdit", () => {
    interceptMe();
    cy.intercept("GET", "**/api/star-systems", { statusCode: 200, body: [] }).as("getSystems");
    cy.intercept("GET", "**/api/missions/mission-cs", {
      statusCode: 200,
      body: {
        id: "mission-cs",
        name: "Circuit Mission",
        starSystemId: "s1",
        missionType: "CIRCUIT_SIMULATION",
        difficulty: "EASY",
        orderInSystem: 1,
      },
    }).as("getMission");

    cy.visit("/#/admin/missions/mission-cs", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });
    cy.wait("@getMission");
    cy.get("[data-cy='circuit-forge-btn']").should("be.visible");
  });

  // ---------------------------------------------------------------------------
  // 12. Circuit Forge button NOT visible on CODING mission
  // ---------------------------------------------------------------------------
  it("does not show Circuit Forge button on CODING mission", () => {
    interceptMe();
    cy.intercept("GET", "**/api/star-systems", { statusCode: 200, body: [] }).as("getSystems");
    cy.intercept("GET", "**/api/missions/mission-coding", {
      statusCode: 200,
      body: {
        id: "mission-coding",
        name: "Coding Mission",
        starSystemId: "s1",
        missionType: "CODING",
        difficulty: "EASY",
        orderInSystem: 1,
      },
    }).as("getMission");

    cy.visit("/#/admin/missions/mission-coding", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });
    cy.wait("@getMission");
    cy.get("[data-cy='circuit-forge-btn']").should("not.exist");
  });
});
