/// <reference types="cypress" />
import { API, uid, visitWithToken, adminCreds, apiLogin } from "../../support/integration";

describe("Admin — Star System CRUD (valódi backend)", () => {
  let token: string;
  let createdSystemId: string;
  const systemName = `CY Szektór ${uid()}`;

  before(() => {
    const { username, password } = adminCreds();
    apiLogin(username, password).then((t) => {
      token = t;
    });
  });

  after(() => {
    if (createdSystemId && token) {
      cy.request({
        method: "DELETE",
        url: `${API()}/star-systems/${createdSystemId}`,
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      });
    }
  });

  it("új star system létrehozása", () => {
    visitWithToken("/#/admin/star-systems/new", token);

    cy.get('input[name="name"]').type(systemName);
    cy.get('textarea[name="description"]').type("Cypress integrációs teszt szektora");
    cy.contains("Mentés").click();

    // Átirányít a listára
    cy.url().should("include", "/admin/star-systems");
    cy.contains(systemName).should("be.visible");

    // ID kimentése a cleanup-hoz
    cy.request({
      method: "GET",
      url: `${API()}/star-systems`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((res) => {
      const system = res.body.find((s: any) => s.name === systemName);
      expect(system).to.exist;
      createdSystemId = system.id;
    });
  });

  it("star system szerkesztése", () => {
    cy.wrap(null).then(() => {
      expect(createdSystemId, "Star system ID megvan").to.not.be.undefined;
    });

    visitWithToken(`/#/admin/star-systems/${createdSystemId}`, token);

    cy.get('input[name="name"]').clear().type(`${systemName} (módosított)`);
    cy.contains("Mentés").click();

    cy.url().should("include", "/admin/star-systems");

    // Visszaellenőrzés API-n keresztül
    cy.request({
      method: "GET",
      url: `${API()}/star-systems`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((res) => {
      const system = res.body.find((s: any) => s.id === createdSystemId);
      expect(system.name).to.include("módosított");
    });
  });

  it("star system megjelenik a listában", () => {
    visitWithToken("/#/admin/star-systems", token);
    cy.contains(systemName).should("be.visible");
  });

  it("star system törlése", () => {
    cy.wrap(null).then(() => {
      expect(createdSystemId, "Star system ID megvan").to.not.be.undefined;
    });

    visitWithToken("/#/admin/star-systems", token);
    cy.window().then((win) => cy.stub(win, "confirm").returns(true));

    cy.contains(systemName)
      .closest("tr, .MuiTableRow-root, .MuiDataGrid-row")
      .find('button[aria-label="delete"], [data-cy="delete-system-btn"]')
      .first()
      .click({ force: true });

    cy.contains(systemName).should("not.exist");
    createdSystemId = ""; // cleanup-ban ne törölje újra
  });
});
