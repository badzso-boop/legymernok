/// <reference types="cypress" />
import { createMockJwt } from "../support/utils";

describe("Cadet My Forge Page", () => {
  const token = createMockJwt(["ROLE_CADET"]);

  const mockSystems = [
    {
      id: "s1",
      name: "Solar System",
      description: "",
      createdAt: "",
      updatedAt: "",
    },
  ];
  const mockMissions = [
    {
      id: "m1",
      name: "My Test Mission",
      starSystemId: "s1",
      orderInSystem: 1,
      difficulty: "EASY",
      missionType: "CODING",
      verificationStatus: "DRAFT",
    },
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

    cy.intercept("GET", "**/api/missions/my-missions", {
      statusCode: 200,
      body: mockMissions,
    }).as("getMyMissions");
  });

  it("should redirect to /login if not authenticated", () => {
    cy.visit("/#/my-forge");
    cy.url().should("include", "/login");
  });

  it("should render the forge dashboard with systems and missions", () => {
    cy.visit("/#/my-forge", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getMySystems");
    cy.wait("@getMyMissions");

    cy.contains("Solar System").should("be.visible");
    cy.contains("My Test Mission").scrollIntoView().should("be.visible");
  });

  it("should show 0 missions detected in title when no missions exist", () => {
    cy.intercept("GET", "**/api/missions/my-missions", {
      statusCode: 200,
      body: [],
    }).as("getEmptyMissions");

    cy.intercept("GET", "**/api/star-systems/my-systems", {
      statusCode: 200,
      body: [],
    }).as("getEmptySystems");

    cy.visit("/#/my-forge", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getEmptySystems");
    cy.wait("@getEmptyMissions");

    cy.contains("0_MISSIONS_DETECTED").should("be.visible");
  });

  it("should navigate to /forge when New Mission button is clicked", () => {
    cy.visit("/#/my-forge", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getMySystems");
    cy.wait("@getMyMissions");

    cy.get('[data-cy="new-mission-btn"]').click();
    cy.url().should("include", "/forge");
    cy.url().should("not.include", "/my-forge");
  });

  it("should navigate to /forge/:id when Forge button is clicked on a mission", () => {
    cy.visit("/#/my-forge", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getMySystems");
    cy.wait("@getMyMissions");

    cy.get('[data-cy="forge-btn"]').first().click();
    cy.url().should("include", "/forge/m1");
  });
});
