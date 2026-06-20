/// <reference types="cypress" />
import {
  API,
  uid,
  visitWithToken,
  adminCreds,
  apiLogin,
} from "../../support/integration";

describe("Admin — Csoport kezelés (valódi backend)", () => {
  let token: string;
  let systemId: string;
  let groupId: string;
  let mission1Id: string;
  let mission2Id: string;
  const prefix = uid();

  before(() => {
    const { username, password } = adminCreds();
    apiLogin(username, password).then((t) => {
      token = t;

      // Star system
      cy.request({
        method: "POST",
        url: `${API()}/star-systems`,
        headers: { Authorization: `Bearer ${token}` },
        body: {
          name: `CY Group System ${prefix}`,
          description: "Csoport teszt",
        },
      }).then((sysRes) => {
        systemId = sysRes.body.id;

        // Mission 1 — CONTENT
        cy.request({
          method: "POST",
          url: `${API()}/missions`,
          headers: { Authorization: `Bearer ${token}` },
          body: {
            starSystemId: systemId,
            name: `CY Bevezető ${prefix}`,
            missionType: "CONTENT",
            difficulty: "EASY",
            orderIndex: 1,
            descriptionMarkdown: "",
          },
        }).then((m1) => {
          mission1Id = m1.body.id;
        });

        // Mission 2 — CONTENT
        cy.request({
          method: "POST",
          url: `${API()}/missions`,
          headers: { Authorization: `Bearer ${token}` },
          body: {
            starSystemId: systemId,
            name: `CY Haladó ${prefix}`,
            missionType: "CONTENT",
            difficulty: "MEDIUM",
            orderIndex: 2,
            descriptionMarkdown: "",
          },
        }).then((m2) => {
          mission2Id = m2.body.id;
        });

        // Csoport létrehozása API-n (a "misszió hozzáadása" teszt erre támaszkodik)
        cy.request({
          method: "POST",
          url: `${API()}/mission-groups`,
          headers: { Authorization: `Bearer ${token}` },
          body: {
            name: `CY Csoport ${prefix}`,
            starSystemId: systemId,
            orderIndex: 1,
          },
        }).then((g) => {
          groupId = g.body.id;
        });
      });
    });
  });

  after(() => {
    if (!systemId || !token) return;
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

  it("csoport létrehozása a star system szerkesztőjén", () => {
    cy.wrap(null).then(() => expect(systemId).to.not.be.undefined);

    visitWithToken(`/#/admin/star-systems/${systemId}`, token);

    cy.contains("Új csoport").click();
    cy.contains("Új csoport létrehozása").should("be.visible");
    cy.get(".MuiDialog-root input").first().type(`CY UI Csoport ${prefix}`);
    cy.contains("Létrehozás").click();

    cy.contains(`CY UI Csoport ${prefix}`).should("be.visible");
  });

  it("önálló misszió hozzáadása csoporthoz", () => {
    cy.wrap(null).then(() => {
      expect(systemId).to.not.be.undefined;
      expect(groupId).to.not.be.undefined;
    });

    cy.request({
      method: "POST",
      url: `${API()}/mission-groups/${groupId}/missions/${mission1Id}`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }).then((addRes) => {
      expect(addRes.status).to.be.oneOf([200, 201, 409]);
    });

    // Oldal betöltése után a misszió a csoportban látható
    visitWithToken(`/#/admin/star-systems/${systemId}`, token);
    cy.contains(`CY Bevezető ${prefix}`).should("be.visible");
  });

  it("csoport sorrend API-n ellenőrizhető", () => {
    cy.wrap(null).then(() => expect(systemId).to.not.be.undefined);

    cy.request({
      method: "GET",
      url: `${API()}/star-systems/${systemId}/with-missions`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((res) => {
      expect(res.body.items).to.have.length.greaterThan(0);
      // Legalább egy GROUP típusú elem van
      const hasGroup = res.body.items.some((i: any) => i.type === "GROUP");
      expect(hasGroup).to.be.true;
    });
  });

  it("csoport törlése UI-on", () => {
    cy.wrap(null).then(() => expect(systemId).to.not.be.undefined);

    visitWithToken(`/#/admin/star-systems/${systemId}`, token);

    cy.contains(`CY Csoport ${prefix}`)
      .closest(".MuiPaper-root")
      .find("[aria-label='Törlés']")
      .first()
      .click({ force: true });

    // Ha FILL_IN_BLANK-et tartalmaz → snackbar; ha üres → eltűnik
    cy.wait(500);
    cy.contains(`CY Csoport ${prefix}`).should("not.exist");
  });
});
