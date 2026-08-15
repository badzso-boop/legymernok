/// <reference types="cypress" />
import { createMockJwt } from "../support/utils";

describe("Admin User List (Mocked Backend)", () => {
  beforeEach(() => {
    // 1. Mock: Auth /me
    cy.intercept("GET", "**/api/auth/me", {
      statusCode: 200,
      body: {
        username: "cypress_admin",
        roles: ["ROLE_ADMIN"],
      },
    }).as("getMe");

    // 2. Mock: Users
    cy.intercept("GET", "**/api/users", {
      statusCode: 200,
      body: [
        {
          id: "mock-1",
          username: "cypress_admin",
          email: "cypress@test.com",
          roles: ["ROLE_ADMIN"],
          createdAt: "2024-01-01T10:00:00Z",
          avatarUrl: null,
          updatedAt: "2024-01-02T10:00:00Z",
        },
      ],
    }).as("getUsers");
  });

  it("should display the user list when logged in as admin", () => {
    const token = createMockJwt(["ROLE_ADMIN"]);

    cy.visit("/#/admin/users", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getUsers");

    cy.url().should("include", "/#/admin/users");
    cy.contains("cypress_admin").should("be.visible");
  });

  it("should redirect to home when logged in as simple user", () => {
    const token = createMockJwt(["ROLE_CADET"]);

    // Itt a mocknak is cadet jogot kell visszaadnia!
    cy.intercept("GET", "**/api/auth/me", {
      statusCode: 200,
      body: { username: "cadet", roles: ["ROLE_CADET"] },
    }).as("getMeCadet");

    cy.visit("/#/admin/users", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMeCadet");

    // Ellenőrzés: visszadobott?
    cy.url().should("eq", Cypress.config().baseUrl + "/#/");
  });
});
