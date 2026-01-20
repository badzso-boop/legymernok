/// <reference types="cypress" />
import { createMockJwt } from "../support/utils";

describe("Admin Star System Management (Mocked Backend)", () => {
  const token = createMockJwt(["ROLE_ADMIN"]);

  beforeEach(() => {
    // 1. Mock: Auth /me
    cy.intercept("GET", "**/api/auth/me", {
      statusCode: 200,
      body: {
        username: "cypress_admin",
        roles: ["ROLE_ADMIN"],
      },
    }).as("getMe");

    // 2. Mock: Star Systems
    cy.intercept("GET", "**/api/star-systems", {
      statusCode: 200,
      body: [
        {
          id: "system-1",
          name: "Coruscant",
          description: "City planet",
          createdAt: "2024-01-01T10:00:00Z",
          updatedAt: "2024-01-01T10:00:00Z",
        },
      ],
    }).as("getSystems");
  });

  it("should list star systems", () => {
    cy.visit("/#/admin/star-systems", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getSystems");
    cy.contains("Coruscant").should("be.visible");
  });

  it("should create a new star system", () => {
    cy.intercept("POST", "**/api/star-systems", {
      statusCode: 200,
      body: { id: "new-1", name: "Dagobah" },
    }).as("createSystem");

    cy.visit("/#/admin/star-systems/new", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");

    cy.get('input[name="name"]').type("Dagobah");
    cy.get('textarea[name="description"]').type("Swamp planet");
    cy.get("button").contains("Mentés").click();

    cy.wait("@createSystem");
    cy.url().should("include", "/#/admin/star-systems");
  });

  it("should edit an existing star system", () => {
    cy.intercept("GET", "**/api/star-systems/system-1/with-missions", {
      statusCode: 200,
      body: {
        id: "system-1",
        name: "Coruscant",
        description: "City planet",
        iconUrl: "",
        missions: [],
      },
    }).as("getSystemDetail");

    cy.intercept("PUT", "**/api/star-systems/system-1", {
      statusCode: 200,
      body: {},
    }).as("updateSystem");

    cy.visit("/#/admin/star-systems/system-1", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getSystemDetail");

    cy.get('input[name="name"]').should("have.value", "Coruscant");
    cy.get('input[name="name"]').clear().type("Coruscant Updated");
    cy.get("button").contains("Mentés").click();

    cy.wait("@updateSystem");
  });
});
