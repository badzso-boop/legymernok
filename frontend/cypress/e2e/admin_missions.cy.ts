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

    // Select kezelése MUI-nál (ID alapján a legstabilabb)
    // Megnyitjuk a legördülőt
    cy.get("#mui-component-select-starSystemId").parent().click();

    // Válasszuk ki az opciót a listából (ami a body végére kerül)
    cy.get('ul[role="listbox"]').contains("Alpha Centauri").click();

    // Ezzel triggereljük a rendszerváltást -> next-order hívás
    cy.wait("@getNextOrder");

    // Ellenőrizzük, hogy beírta-e az 5-ös sorszámot
    cy.get('input[name="orderInSystem"]').should("have.value", "5");

    // Mentés gomb keresése nyelvfüggetlenül (ikon alapján) vagy fallback szöveggel
    // Ha a gombon SaveIcon van, az SVG-t keressük
    cy.get("button").find('svg[data-testid="SaveIcon"]').click({ force: true });

    cy.wait("@createMission").its("request.body").should("deep.include", {
      name: "Looping",
      starSystemId: "s2",
      orderInSystem: 5, // Figyelem: string vs number konverzió lehet a frontenden
    });

    // Visszairányítás ellenőrzése
    cy.url().should("include", "/admin/star-systems/s2");
  });

  it("should delete a mission", () => {
    // Mock: Delete
    cy.intercept("DELETE", "**/api/missions/m1", {
      statusCode: 204,
    }).as("deleteMission");

    // A delete utáni újratöltést is mockolni kell (üres listával tér vissza)
    cy.intercept("GET", "**/api/missions", {
      statusCode: 200,
      body: [],
    }).as("getMissionsEmpty");

    cy.visit("/#/admin/missions", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMissions"); // Első betöltés (van adat)

    // Törlés gomb (DataGrid actions oszlop)
    // Keressük a Delete ikont (MUI SVG) a gombokban
    // A .should('exist') segít a várakozásban, ha a grid lassan renderel
    cy.get("button")
      .find('svg[data-testid="DeleteIcon"]')
      .should("exist")
      .first()
      .click({ force: true });

    // Confirm ablak kezelése (Cypress automatikusan elfogadja, de ellenőrizhetjük)

    cy.wait("@deleteMission");

    // A második getMissions hívást várjuk (ami már üres)
    // Megjegyzés: mivel a hívások ugyanazon az URL-en mennek, lehet, hogy alias nélkül vagy
    //@getMissions-ként kapja el újra.
    // De mivel felülírtuk (vagy a wait sorrend számít), figyeljük a hívást.
  });
});
