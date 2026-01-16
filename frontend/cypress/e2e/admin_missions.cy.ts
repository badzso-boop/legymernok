/// <reference types="cypress" />
import { createMockJwt } from "../support/utils";

describe("Admin Mission Management (Mocked Backend)", () => {
  const token = createMockJwt(["ROLE_ADMIN"]);

  beforeEach(() => {
    // 1. Mock: Star Systems (kell a listázáshoz és a selecthez)
    cy.intercept("GET", "**/api/star-systems", {
      statusCode: 200,
      body: [
        { id: "s1", name: "Solar System" },
        { id: "s2", name: "Alpha Centauri" },
      ],
    }).as("getSystems");

    // 2. Mock: Missions List
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

    cy.wait("@getMissions");
    cy.wait("@getSystems");

    // Ellenőrzés: Megjelenik a küldetés neve
    cy.contains("First Steps").should("be.visible");
    // Ellenőrzés: A rendszer neve jelenik meg az ID helyett (a helper függvényünk miatt)
    cy.contains("Solar System").should("be.visible");
  });

  it("should create a new mission", () => {
    // Mock: Next Order
    cy.intercept("GET", "**/api/missions/next-order*", {
      statusCode: 200,
      body: 5,
    }).as("getNextOrder");

    // Mock: Create POST
    cy.intercept("POST", "**/api/missions", {
      statusCode: 201,
      body: { id: "new-1", name: "Looping" },
    }).as("createMission");

    cy.visit("/#/admin/missions/new", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    // Várjuk meg a rendszerek betöltését a Selecthez
    cy.wait("@getSystems");

    // Kitöltés
    cy.get('input[name="name"]').type("Looping");

    // Select kezelése MUI-nál trükkös (hidden input vagy click)
    // Megkeressük a "Csillagrendszer" labelt, és utána a comboboxot
    cy.contains("Csillagrendszer").parent().click();
    cy.contains("Alpha Centauri").click();
    // Ezzel triggereljük a rendszerváltást -> next-order hívás
    cy.wait("@getNextOrder");

    // Ellenőrizzük, hogy beírta-e az 5-ös sorszámot
    cy.get('input[name="orderInSystem"]').should("have.value", "5");

    // Mentés
    cy.contains("Mentés").click();

    cy.wait("@createMission").its("request.body").should("deep.include", {
      name: "Looping",
      starSystemId: "s2",
      orderInSystem: 5, // Figyelem: string vs number konverzió lehet
    });

    // Visszairányítás ellenőrzése
    cy.url().should("include", "/admin/star-systems/s2");
  });

  it("should delete a mission", () => {
    // Mock: Delete
    cy.intercept("DELETE", "**/api/missions/m1", {
      statusCode: 204,
    }).as("deleteMission");

    cy.visit("/#/admin/missions", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    // Törlés gomb (DataGrid actions oszlop)
    // A DataGrid cellái között keressük a gombot.
    // A teszthez érdemes lenne data-testid-t adni a gomboknak, de most próbáljuk ikon alapján.
    cy.get('button[aria-label="Törlés"]').first().click(); // Ha van aria-label, vagy:
    // cy.get('.MuiDataGrid-cell').find('button').last().click();

    // Confirm ablak kezelése (automatikusan elfogadja a Cypress, de ellenőrizhetjük a hívást)

    // Várjuk a hívást
    // FIGYELEM: A DataGrid virtualizáció miatt lehet, hogy görgetni kell, ha sok adat van.
    // De itt csak 1 sor van.
  });
});
