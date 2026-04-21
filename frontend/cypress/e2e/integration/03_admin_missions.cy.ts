/// <reference types="cypress" />
import { API, uid, visitWithToken, adminCreds, apiLogin } from "../../support/integration";

describe("Admin — Misszió létrehozás és szerkesztés (valódi backend)", () => {
  let token: string;
  let systemId: string;
  const prefix = uid();

  before(() => {
    const { username, password } = adminCreds();
    apiLogin(username, password).then((t) => {
      token = t;
      // Star system létrehozása API-n keresztül
      cy.request({
        method: "POST",
        url: `${API()}/star-systems`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name: `CY System ${prefix}`, description: "Teszt" },
      }).then((res) => {
        systemId = res.body.id;
      });
    });
  });

  after(() => {
    if (!systemId || !token) return;
    // Missziók törlése, majd rendszer törlése
    cy.request({
      method: "GET",
      url: `${API()}/missions?starSystemId=${systemId}`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }).then((res) => {
      if (res.status !== 200) return;
      res.body.forEach((m: any) => {
        cy.request({
          method: "DELETE",
          url: `${API()}/missions/${m.id}`,
          headers: { Authorization: `Bearer ${token}` },
          failOnStatusCode: false,
        });
      });
    });
    cy.request({
      method: "DELETE",
      url: `${API()}/star-systems/${systemId}`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    });
  });

  // ─── CONTENT misszió ───────────────────────────────────────────────────────

  it("CONTENT misszió létrehozása admin UI-on", () => {
    cy.wrap(null).then(() => expect(systemId).to.not.be.undefined);

    visitWithToken(`/#/admin/missions/new?starSystemId=${systemId}`, token);

    cy.get('input[name="name"]').type(`CY Content ${prefix}`);

    // Típus: CONTENT
    cy.get("#mui-component-select-missionType").parent().click();
    cy.get('ul[role="listbox"]').contains("CONTENT").click();

    cy.contains("button", "Létrehozás").click({ force: true });

    cy.url().should("include", `/admin/star-systems/${systemId}`);
  });

  it("CONTENT tartalom szerkesztése", () => {
    cy.wrap(null).then(() => expect(systemId).to.not.be.undefined);

    // Misszió keresése API-n keresztül
    cy.request({
      method: "GET",
      url: `${API()}/missions?starSystemId=${systemId}`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((res) => {
      const mission = res.body.find((m: any) => m.name.includes("CY Content"));
      expect(mission).to.exist;

      visitWithToken(`/#/admin/missions/${mission.id}`, token);

      cy.contains("Tartalom").click();

      // Markdown szöveg megadása
      cy.get("textarea").first().clear({ force: true }).type("# Bevezetés\n\nEz egy valódi content misszió.", { force: true });
      cy.contains("MENTÉS").click({ force: true });

      // A tartalom mentve van
      cy.contains("MENTÉS").should("be.visible");
    });
  });

  // ─── FILL_IN_BLANK misszió ─────────────────────────────────────────────────

  it("FILL_IN_BLANK misszió létrehozása és szerkesztése", () => {
    cy.wrap(null).then(() => expect(systemId).to.not.be.undefined);

    visitWithToken(`/#/admin/missions/new?starSystemId=${systemId}`, token);

    cy.get('input[name="name"]').type(`CY FIB ${prefix}`);

    cy.get("#mui-component-select-missionType").parent().click();
    cy.get('ul[role="listbox"]').contains("FILL IN BLANK").click();

    cy.contains("button", "Létrehozás").click({ force: true });

    cy.url().should("include", "/admin/missions/");

    // FIB szerkesztő megnyitása
    cy.request({
      method: "GET",
      url: `${API()}/missions?starSystemId=${systemId}`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((res) => {
      const mission = res.body.find((m: any) => m.name.includes("CY FIB"));
      expect(mission).to.exist;

      visitWithToken(`/#/admin/missions/${mission.id}`, token);

      cy.contains("Kitöltős szerkesztő").click();

      cy.get("textarea").first().clear({ force: true }).type("A [[blank_1]] az egyik legismertebb programozási konstrukció.", { force: true });

      // Opciók hozzáadása a detektált blank-hez
      cy.contains("[[blank_1]]").should("be.visible");

      cy.contains("MENTÉS").click({ force: true });

      // Nincs hibaüzenet
      cy.contains("ERROR").should("not.exist");
    });
  });

  // ─── Misszió törlése ───────────────────────────────────────────────────────

  it("missziók törlése az admin listáról", () => {
    cy.wrap(null).then(() => expect(systemId).to.not.be.undefined);

    visitWithToken("/#/admin/missions", token);
    cy.window().then((win) => cy.stub(win, "confirm").returns(true));

    cy.contains(`CY Content ${prefix}`)
      .closest("tr, .MuiTableRow-root, .MuiDataGrid-row")
      .find('[data-cy="delete-mission-btn"]')
      .first()
      .click({ force: true });

    cy.contains(`CY Content ${prefix}`).should("not.exist");

    cy.contains(`CY FIB ${prefix}`)
      .closest("tr, .MuiTableRow-root, .MuiDataGrid-row")
      .find('[data-cy="delete-mission-btn"]')
      .first()
      .click({ force: true });

    cy.contains(`CY FIB ${prefix}`).should("not.exist");
  });
});
