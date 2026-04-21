/// <reference types="cypress" />
import { API, uid, visitWithToken, adminCreds, cadetCreds, apiLogin } from "../../support/integration";

describe("User — Group Player teljes flow (valódi backend)", () => {
  let adminToken: string;
  let cadetToken: string;
  let systemId: string;
  let groupId: string;
  let contentMissionId: string;
  let fibMissionId: string;
  const prefix = uid();

  before(() => {
    const admin = adminCreds();
    const cadet = cadetCreds();

    // Admin token
    apiLogin(admin.username, admin.password).then((t) => {
      adminToken = t;

      // Star system létrehozása
      cy.request({
        method: "POST",
        url: `${API()}/star-systems`,
        headers: { Authorization: `Bearer ${adminToken}` },
        body: { name: `CY Player System ${prefix}`, description: "Player teszt" },
      }).then((sysRes) => {
        systemId = sysRes.body.id;

        // CONTENT misszió
        cy.request({
          method: "POST",
          url: `${API()}/missions`,
          headers: { Authorization: `Bearer ${adminToken}` },
          body: {
            starSystemId: systemId,
            name: `CY Elmélet ${prefix}`,
            missionType: "CONTENT",
            difficulty: "EASY",
            orderIndex: 1,
            descriptionMarkdown: "",
            content: "# Bevezetés\n\nÜdvözöllek a Cypress integrációs tesztben!\n\nEz az első fejezet tartalma.",
          },
        }).then((m1) => {
          contentMissionId = m1.body.id;
        });

        // FILL_IN_BLANK misszió
        cy.request({
          method: "POST",
          url: `${API()}/missions`,
          headers: { Authorization: `Bearer ${adminToken}` },
          body: {
            starSystemId: systemId,
            name: `CY Kitöltős ${prefix}`,
            missionType: "FILL_IN_BLANK",
            difficulty: "EASY",
            orderIndex: 2,
            descriptionMarkdown: "",
          },
        }).then((m2) => {
          fibMissionId = m2.body.id;

          // FIB definíció létrehozása
          cy.request({
            method: "POST",
            url: `${API()}/missions/${fibMissionId}/fill-in-blank`,
            headers: { Authorization: `Bearer ${adminToken}` },
            body: {
              templateText: "A programozás alapja a [[blank_1]].",
              passThreshold: 1,
              blanks: [
                {
                  key: "blank_1",
                  orderIndex: 0,
                  options: [
                    { optionText: "logika", correct: true, orderIndex: 0 },
                    { optionText: "mágia", correct: false, orderIndex: 1 },
                    { optionText: "szerencse", correct: false, orderIndex: 2 },
                  ],
                },
              ],
            },
          });
        });

        // Csoport létrehozása
        cy.request({
          method: "POST",
          url: `${API()}/mission-groups`,
          headers: { Authorization: `Bearer ${adminToken}` },
          body: {
            name: `CY Teszt Csoport ${prefix}`,
            starSystemId: systemId,
            orderIndex: 1,
          },
        }).then((grpRes) => {
          groupId = grpRes.body.id;

          // Missziók hozzáadása a csoporthoz
          cy.request({
            method: "POST",
            url: `${API()}/mission-groups/${groupId}/missions/${contentMissionId}`,
            headers: { Authorization: `Bearer ${adminToken}` },
          });
          cy.request({
            method: "POST",
            url: `${API()}/mission-groups/${groupId}/missions/${fibMissionId}`,
            headers: { Authorization: `Bearer ${adminToken}` },
          });
        });
      });
    });

    // Cadet token
    apiLogin(cadet.username, cadet.password).then((t) => {
      cadetToken = t;
    });
  });

  after(() => {
    if (!systemId || !adminToken) return;
    cy.request({
      method: "GET",
      url: `${API()}/missions?starSystemId=${systemId}`,
      headers: { Authorization: `Bearer ${adminToken}` },
      failOnStatusCode: false,
    }).then((res) => {
      if (res.status !== 200) return;
      res.body.forEach((m: any) => {
        cy.request({
          method: "DELETE",
          url: `${API()}/missions/${m.id}`,
          headers: { Authorization: `Bearer ${adminToken}` },
          failOnStatusCode: false,
        });
      });
    });
    cy.request({
      method: "DELETE",
      url: `${API()}/star-systems/${systemId}`,
      headers: { Authorization: `Bearer ${adminToken}` },
      failOnStatusCode: false,
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 1. Star System Detail — NOT_STARTED állapot
  // ─────────────────────────────────────────────────────────────────────────

  it("star system detail oldalon megjelenik a csoport KEZDD EL gombbal", () => {
    cy.wrap(null).then(() => {
      expect(systemId).to.not.be.undefined;
      expect(cadetToken).to.not.be.undefined;
    });

    visitWithToken(`/#/star-systems/${systemId}`, cadetToken);

    cy.contains(`CY Teszt Csoport ${prefix}`).should("be.visible");
    cy.contains("KEZDD EL").should("be.visible");
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 2. Group Player betölt — CONTENT lépés
  // ─────────────────────────────────────────────────────────────────────────

  it("group player megnyílik és CONTENT tartalom jelenik meg", () => {
    cy.wrap(null).then(() => expect(groupId).to.not.be.undefined);

    visitWithToken(`/#/play/group/${groupId}`, cadetToken);

    // Lépésjelző: 1 / 2
    cy.contains("1 / 2").should("be.visible");

    // Markdown tartalom megjelenik
    cy.contains("Bevezetés").should("be.visible");
    cy.contains("Cypress integrációs").should("be.visible");
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 3. CONTENT → KÖVETKEZŐ → FIB lépés
  // ─────────────────────────────────────────────────────────────────────────

  it("CONTENT lépés után KÖVETKEZŐ → FIB betölt", () => {
    cy.wrap(null).then(() => expect(groupId).to.not.be.undefined);

    visitWithToken(`/#/play/group/${groupId}`, cadetToken);

    // KÖVETKEZŐ gomb kattintás
    cy.contains("KÖVETKEZŐ").closest(".button-group").find("button").click({ force: true });

    // Lépésjelző: 2 / 2
    cy.contains("2 / 2").should("be.visible");

    // FIB tartalom megjelenik
    cy.contains("logika").should("be.visible");
    cy.contains("programozás").should("be.visible");
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 4. FIB kitöltése és beküldése
  // ─────────────────────────────────────────────────────────────────────────

  it("FIB kitöltése helyes válasszal → TELJESÍTVE", () => {
    cy.wrap(null).then(() => expect(groupId).to.not.be.undefined);

    visitWithToken(`/#/play/group/${groupId}`, cadetToken);

    // Várjuk a FIB lépést (progress már 1 lépést mutat az előző teszt után)
    cy.contains("2 / 2", { timeout: 8000 }).should("be.visible");

    // Helyes opcióra kattintás ("logika")
    cy.contains("logika").click();

    // Beküldés
    cy.contains("BEKÜLDÉS").closest(".button-group").find("button").click({ force: true });

    // Eredmény: TELJESÍTVE
    cy.contains("TELJESÍTVE").should("be.visible");
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 5. Csoport befejezése
  // ─────────────────────────────────────────────────────────────────────────

  it("KÖVETKEZŐ az utolsó lépés után → CSOPORT TELJESÍTVE képernyő", () => {
    cy.wrap(null).then(() => expect(groupId).to.not.be.undefined);

    visitWithToken(`/#/play/group/${groupId}`, cadetToken);

    // Az előző tesztek után a FIB lépés már teljesített → "Már teljesítetted" banner
    cy.contains("2 / 2", { timeout: 8000 }).should("be.visible");

    cy.contains("KÖVETKEZŐ").closest(".button-group").find("button").click({ force: true });

    cy.contains("CSOPORT TELJESÍTVE").should("be.visible");
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 6. Star System Detail — ✓ KÉSZ badge
  // ─────────────────────────────────────────────────────────────────────────

  it("vissza a rendszerhez → ✓ KÉSZ badge látható", () => {
    cy.wrap(null).then(() => {
      expect(systemId).to.not.be.undefined;
      expect(groupId).to.not.be.undefined;
    });

    visitWithToken(`/#/star-systems/${systemId}`, cadetToken);

    cy.contains("✓ KÉSZ").should("be.visible");
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 7. Folytatás — IN_PROGRESS állapot (progress API ellenőrzés)
  // ─────────────────────────────────────────────────────────────────────────

  it("GROUP_PROGRESS API visszaadja a helyes completed állapotot", () => {
    cy.wrap(null).then(() => expect(groupId).to.not.be.undefined);

    cy.request({
      method: "GET",
      url: `${API()}/group-progress/${groupId}`,
      headers: { Authorization: `Bearer ${cadetToken}` },
    }).then((res) => {
      expect(res.status).to.equal(200);
      expect(res.body.completed).to.be.true;
      expect(res.body.completedCount).to.equal(res.body.totalCount);
    });
  });
});
