/// <reference types="cypress" />
import { createMockJwt } from "../support/utils";

describe("Admin Mission Management (Mocked Backend)", () => {
  const token = createMockJwt(["ROLE_ADMIN"]);

  beforeEach(() => {
    // 1. Mock: Auth /me végpont (ÚJ!)
    cy.intercept("GET", "**/api/auth/me", {
      statusCode: 200,
      body: {
        id: "mock-admin-id",
        username: "cypress_admin",
        email: "admin@test.com",
        fullName: "Cypress Admin",
        roles: ["ROLE_ADMIN"],
      },
    }).as("getMe");

    // 2. Mock: Star Systems
    cy.intercept("GET", "**/api/star-systems", {
      statusCode: 200,
      body: [
        { id: "s1", name: "Solar System" },
        { id: "s2", name: "Alpha Centauri" },
      ],
    }).as("getSystems");

    // 3. Mock: Missions List
    cy.intercept("GET", "**/api/missions", {
      statusCode: 200,
      body: [
        {
          id: "m1",
          name: "First Steps",
          starSystemId: "s1",
          orderInSystem: 1,
          difficulty: "EASY",
          missionType: "CODING",
        },
      ],
    }).as("getMissions");
  });

  it("should list missions", () => {
    cy.visit("/#/admin/missions", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe"); // Megvárjuk az autentikációt
    cy.wait("@getMissions");
    cy.wait("@getSystems");

    cy.contains("First Steps").should("be.visible");
    cy.contains("Solar System").should("be.visible");
  });

  it("should create a new mission", () => {
    cy.intercept("GET", "**/api/missions/next-order*", {
      statusCode: 200,
      body: 5,
    }).as("getNextOrder");

    cy.intercept("POST", "**/api/missions", {
      statusCode: 201,
      body: { id: "new-1", name: "Looping" },
    }).as("createMission");

    cy.visit("/#/admin/missions/new", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe"); // Auth
    cy.wait("@getSystems");

    cy.get('input[name="name"]').type("Looping");

    cy.get("#mui-component-select-starSystemId").parent().click();
    cy.get('ul[role="listbox"]').contains("Alpha Centauri").click();

    cy.wait("@getNextOrder");

    cy.get('input[name="orderInSystem"]').should("have.value", "5");

    cy.get("button").find('svg[data-testid="SaveIcon"]').click({ force: true });

    cy.wait("@createMission");
    cy.url().should("include", "/admin/star-systems/s2");
  });

  it("should delete a mission", () => {
    cy.intercept("DELETE", "**/api/missions/m1", { statusCode: 204 }).as(
      "deleteMission",
    );

    // Külön interceptorok az állapotváltáshoz
    cy.intercept("GET", "**/api/missions", {
      statusCode: 200,
      body: [
        {
          id: "m1",
          name: "First Steps",
          starSystemId: "s1",
          orderInSystem: 1,
          difficulty: "EASY",
          missionType: "CODING",
        },
      ],
    }).as("getMissionsInitial");

    cy.visit("/#/admin/missions", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe"); // Auth
    cy.wait("@getMissionsInitial");
    cy.contains("First Steps").should("be.visible");

    // Felülírjuk az interceptort ÜRES válaszra a törlés utánra
    cy.intercept("GET", "**/api/missions", { statusCode: 200, body: [] }).as(
      "getMissionsEmpty",
    );

    cy.get("button")
      .find('svg[data-testid="DeleteIcon"]')
      .should("exist")
      .first()
      .click({ force: true });

    cy.wait("@deleteMission");
    cy.wait("@getMissionsEmpty");

    cy.contains("First Steps").should("not.exist");
  });
});
