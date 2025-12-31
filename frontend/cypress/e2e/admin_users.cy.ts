import { createMockJwt } from "../support/utils";

describe("Admin User List (Mocked Backend)", () => {
  beforeEach(() => {
    // Minden teszt előtt beállítjuk a mock API választ
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
          updatedAt: "2024-01-02T10:00:00Z", // Hozzáadva a biztonság kedvéért
        },
      ],
    }).as("getUsers");
  });

  it("should display the user list when logged in as admin", () => {
    // 1. Token generálása ADMIN joggal
    const token = createMockJwt(["ROLE_ADMIN"]);

    // 2. Látogatás úgy, hogy ELŐTTE beállítjuk a tokent
    // Az onBeforeLoad garantálja, hogy a token ott legyen, mielőtt a React elindul
    cy.visit("/admin/users", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    // 3. Várjuk meg, amíg az oldal betölt és meghívja az API-t
    cy.wait("@getUsers");

    // 4. Ellenőrzés: URL nem változott meg (tehát nem dobott ki a / vagy /login oldalra)
    cy.url().should("include", "/admin/users");

    // 5. Tartalmi ellenőrzés
    cy.contains("cypress_admin").should("be.visible");
    cy.contains("cypress@test.com").should("be.visible");
  });

  it("should redirect to home when logged in as simple user", () => {
    // 1. Token generálása CSAK CADET joggal
    const token = createMockJwt(["ROLE_CADET"]);

    cy.visit("/admin/users", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    // 2. Ellenőrzés: Visszadobott a főoldalra?
    cy.url().should("eq", Cypress.config().baseUrl + "/");
  });
});
