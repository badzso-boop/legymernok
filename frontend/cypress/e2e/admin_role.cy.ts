/// <reference types="cypress" />
import { createMockJwt } from "../support/utils";

describe("Admin Role Management (Mocked Backend)", () => {
  const token = createMockJwt(["ROLE_ADMIN"]);

  beforeEach(() => {
    // 1. Mock: Auth /me
    cy.intercept("GET", "**/api/auth/me", {
      statusCode: 200,
      body: { username: "admin", roles: ["ROLE_ADMIN"] },
    }).as("getMe");

    // 2. Mock: Permissions (mindig kell a RoleEdit-hez)
    cy.intercept("GET", "**/api/roles/permissions", {
      statusCode: 200,
      body: [{ id: "p1", name: "mission:read", description: "Read missions" }],
    }).as("getPermissions");

    // 3. Mock: Roles List
    cy.intercept("GET", "**/api/roles", {
      statusCode: 200,
      body: [
        {
          id: "r1",
          name: "ROLE_TEST",
          description: "Test role",
          permissions: [],
        },
      ],
    }).as("getRoles");
  });

  it("should list roles", () => {
    cy.visit("/#/admin/roles", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getRoles");
    cy.contains("ROLE_TEST").should("be.visible");
  });

  it("should create a new role", () => {
    cy.intercept("POST", "**/api/roles", {
      statusCode: 201,
      body: { id: "new-1", name: "ROLE_NEW" },
    }).as("createRole");

    cy.visit("/#/admin/roles/new", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getPermissions"); // A checkboxokhoz kell

    cy.get('input[type="text"]').first().type("ROLE_NEW"); // Név mező (feltételezve, hogy az első)
    // Vagy specifikusabban: cy.contains("Szerepkör neve").parent().find('input').type("ROLE_NEW");

    // Checkbox bejelölése
    cy.contains("mission:read").click();

    // Mentés
    cy.get("button").find('svg[data-testid="SaveIcon"]').click();

    cy.wait("@createRole");
    cy.url().should("include", "/admin/roles");
  });
});
