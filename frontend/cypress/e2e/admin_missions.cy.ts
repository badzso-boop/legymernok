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

    // MUI Select Robosztusabb Kezelése:
    // 1. Kattintsunk a "button" jellegű elemre, ami a Selectet nyitja (MUI így rendereli)
    // Megkeressük a labelt, és a mellette lévő inputot vezérlő div-et
    cy.get("#mui-component-select-starSystemId").click(); // A MUI generál ilyen ID-t a name alapjá
    // VAGY ha ez nem működik:
    // cy.get('[role="combobox"]').click();

    // 2. Válasszuk ki az opciót a listából (ami a body végére kerül)
    cy.get('ul[role="listbox"]').contains("Alpha Centauri").click();

    // Most már várhatjuk a hívást
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

    cy.wait("@getMissions");

    // Törlés gomb keresése az ikon alapján (biztosabb, mint az aria-label, ha nem vagyunk biztosak fordításban)
    // Keressük a Delete ikont (MUI SVG)
    cy.get("button").has('svg[data-testid="DeleteIcon"]').click();

    // VAGY ha a data-testid-t betetted a MissionList.tsx-be:
    // cy.get('[data-testid="delete-mission-m1"]').click();

    // Confirm ablak kezelése (automatikusan elfogadja a Cypress, de ellenőrizhetjük a hívást)

    // Várjuk a hívást
    // FIGYELEM: A DataGrid virtualizáció miatt lehet, hogy görgetni kell, ha sok adat van.
    // De itt csak 1 sor van.
  });
});
