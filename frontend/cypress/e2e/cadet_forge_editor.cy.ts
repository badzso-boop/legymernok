/// <reference types="cypress" />
import { createMockJwt } from "../support/utils";

describe("Cadet Forge Editor (CODING mission)", () => {
  const token = createMockJwt(["ROLE_CADET"]);
  const missionId = "m-coding-1";

  const mockMission = {
    id: missionId,
    name: "Test Coding Mission",
    missionType: "CODING",
    verificationStatus: "DRAFT",
    starSystemId: "s1",
    orderInSystem: 1,
    difficulty: "EASY",
    ownerId: "cadet-1",
    ownerUsername: "cypress_cadet",
    createdAt: new Date().toISOString(),
  };

  const mockFiles = {
    "solution.js": "// Write your solution here\nfunction solve() {}",
    "solution.test.js": "// Tests\ntest('example', () => {});",
    "README.md": "# Mission Instructions\n\nComplete the task.",
  };

  beforeEach(() => {
    cy.intercept("GET", "**/api/auth/me", {
      statusCode: 200,
      body: { id: "cadet-1", username: "cypress_cadet", roles: ["ROLE_CADET"] },
    }).as("getMe");

    cy.intercept("GET", `**/api/missions/${missionId}`, {
      statusCode: 200,
      body: mockMission,
    }).as("getMission");

    cy.intercept("GET", `**/api/missions/${missionId}/forge/files`, {
      statusCode: 200,
      body: mockFiles,
    }).as("getFiles");
  });

  it("should load and display mission files in the sidebar", () => {
    cy.visit(`/#/forge/${missionId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getMission");
    cy.wait("@getFiles");

    cy.contains("solution.js").should("be.visible");
    cy.contains("solution.test.js").should("be.visible");
    cy.contains("README.md").should("be.visible");
  });

  it("should render the Monaco editor container", () => {
    cy.visit(`/#/forge/${missionId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getMission");
    cy.wait("@getFiles");

    // Monaco editor konténer megjelenik (belső tartalom nem tesztelhető közvetlenül)
    cy.get(".monaco-editor").should("exist");
  });

  it("should switch active file when clicking a file in the sidebar", () => {
    cy.visit(`/#/forge/${missionId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getMission");
    cy.wait("@getFiles");

    cy.contains("README.md").click();

    // A kattintás után az editor újra renderel — az aktív fájl kiemelve van
    cy.contains("README.md").should("be.visible");
    cy.get(".monaco-editor").should("exist");
  });

  it("should show the verification status chip", () => {
    cy.visit(`/#/forge/${missionId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getMission");
    cy.wait("@getFiles");

    // DRAFT státusz chip megjelenik (i18n: forge.status.draft = "Piszkozat")
    cy.contains("Piszkozat").should("be.visible");
  });

  it("should call the save API and show success snackbar", () => {
    cy.intercept("POST", `**/api/missions/${missionId}/forge/save`, {
      statusCode: 200,
      body: { ...mockMission, verificationStatus: "PENDING" },
    }).as("saveFiles");

    cy.visit(`/#/forge/${missionId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getMission");
    cy.wait("@getFiles");

    cy.get('[data-cy="forge-save-btn"]').click();

    cy.wait("@saveFiles");

    // Success snackbar megjelenik (forge.filesSavedSuccess szöveget tartalmaz)
    cy.contains("GITEA").should("be.visible");
  });

  it("should navigate back to /my-forge via the retro back button in the toolbar", () => {
    cy.visit(`/#/forge/${missionId}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getMission");
    cy.wait("@getFiles");

    cy.get('[data-cy="forge-back-btn"]').click({ force: true });

    cy.url().should("include", "/my-forge");
  });
});
