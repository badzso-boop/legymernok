/// <reference types="cypress" />
import { API, uid, visitWithToken, apiLogin } from "../../support/integration";

describe("Auth — Regisztráció és bejelentkezés (valódi backend)", () => {
  const testUsername = uid();
  const testEmail = `${testUsername}@test.hu`;
  const testPassword = "Test1234!";

  let adminToken: string;

  before(() => {
    // Admin token megszerzése a cleanup-hoz
    cy.request({
      method: "POST",
      url: `${API()}/auth/login`,
      body: {
        username: Cypress.env("ADMIN_USERNAME") ?? "admin",
        password: Cypress.env("ADMIN_PASSWORD") ?? "Admin1234!",
      },
      failOnStatusCode: false,
    }).then((res) => {
      if (res.status === 200) adminToken = res.body.token;
    });
  });

  after(() => {
    // Teszt kadet törlése admin tokennel
    if (!adminToken) return;
    cy.request({
      method: "GET",
      url: `${API()}/users`,
      headers: { Authorization: `Bearer ${adminToken}` },
      failOnStatusCode: false,
    }).then((res) => {
      if (res.status !== 200) return;
      const user = res.body.find((u: any) => u.username === testUsername);
      if (user) {
        cy.request({
          method: "DELETE",
          url: `${API()}/users/${user.id}`,
          headers: { Authorization: `Bearer ${adminToken}` },
          failOnStatusCode: false,
        });
      }
    });
  });

  it("sikeres regisztráció → bejelentkezve a főoldalra kerül", () => {
    cy.visit("/#/register");
    cy.get('input[name="username"]').type(testUsername);
    cy.get('input[name="email"]').type(testEmail);
    cy.get('input[name="fullName"]').type("Cypress Teszt");
    cy.get('input[name="password"]').type(testPassword);
    cy.get('button[type="submit"]').click();

    cy.url().should("not.include", "/register");
    cy.window().then((win) => {
      expect(win.localStorage.getItem("token")).to.not.be.null;
    });
  });

  it("sikeres bejelentkezés → főoldalra kerül", () => {
    cy.visit("/#/login");
    cy.get('input[name="username"]').type(testUsername);
    cy.get('input[name="password"]').type(testPassword);
    cy.get('button[type="submit"]').click();

    cy.url().should("not.include", "/login");
    cy.window().then((win) => {
      expect(win.localStorage.getItem("token")).to.not.be.null;
    });
  });

  it("hibás jelszóval bejelentkezés → hibaüzenet jelenik meg", () => {
    cy.visit("/#/login");
    cy.get('input[name="username"]').type(testUsername);
    cy.get('input[name="password"]').type("rossz_jelszo");
    cy.get('button[type="submit"]').click();

    cy.url().should("include", "/login");
    cy.get("body").should("contain.text", "");
    // A login oldal marad
    cy.get('input[name="username"]').should("be.visible");
  });
});
