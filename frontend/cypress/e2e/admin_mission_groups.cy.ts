/// <reference types="cypress" />
import { createMockJwt } from "../support/utils";

describe("Admin Mission Group Management (Mocked Backend)", () => {
  const token = createMockJwt(["ROLE_ADMIN"]);

  // --- Közös mock adatok ---

  const mockSystemEmpty = {
    id: "system-1",
    name: "Tatooine",
    description: "Desert planet",
    iconUrl: "",
    createdAt: "2024-01-01T10:00:00Z",
    updatedAt: "2024-01-01T10:00:00Z",
    items: [],
  };

  const mockGroup = {
    id: "group-1",
    name: "Alapok",
    description: null,
    starSystemId: "system-1",
    orderIndex: 1,
    createdAt: "2024-01-01T10:00:00Z",
    updatedAt: "2024-01-01T10:00:00Z",
  };

  const mockStandaloneMission = {
    id: "mission-1",
    name: "Standalone Coding",
    missionType: "CODING",
    difficulty: "EASY",
    starSystemId: "system-1",
    descriptionMarkdown: "",
    templateRepositoryUrl: null,
    orderIndex: 2,
    groupId: null,
    groupOrder: null,
    createdAt: "2024-01-01T10:00:00Z",
  };

  const mockFibMission = {
    id: "fib-mission-1",
    name: "Fill in the Blank",
    missionType: "FILL_IN_BLANK",
    difficulty: "MEDIUM",
    starSystemId: "system-1",
    descriptionMarkdown: "",
    templateRepositoryUrl: null,
    orderIndex: 3,
    groupId: "fib-group-1",
    groupOrder: 1,
    createdAt: "2024-01-01T10:00:00Z",
  };

  const mockFibGroup = {
    id: "fib-group-1",
    name: "FIB Csoport",
    description: null,
    starSystemId: "system-1",
    orderIndex: 2,
    createdAt: "2024-01-01T10:00:00Z",
    updatedAt: "2024-01-01T10:00:00Z",
  };

  beforeEach(() => {
    cy.intercept("GET", "**/api/auth/me", {
      statusCode: 200,
      body: { username: "cypress_admin", roles: ["ROLE_ADMIN"] },
    }).as("getMe");
  });

  // ─────────────────────────────────────────────
  // 1. Group létrehozás
  // ─────────────────────────────────────────────
  it("should create a new mission group", () => {
    // LIFO fix: reload response ELŐSZÖR (stack aljára), initial load UTOLJÁRA times:1-gyel (stack tetejére)
    cy.intercept("GET", "**/api/star-systems/system-1/with-missions", {
      statusCode: 200,
      body: {
        ...mockSystemEmpty,
        items: [
          {
            type: "GROUP",
            orderIndex: 1,
            group: mockGroup,
            groupMissions: [],
          },
        ],
      },
    }).as("getSystemWithGroup");

    cy.intercept(
      {
        method: "GET",
        url: "**/api/star-systems/system-1/with-missions",
        times: 1,
      },
      { statusCode: 200, body: mockSystemEmpty },
    ).as("getSystem");

    cy.intercept("POST", "**/api/mission-groups", {
      statusCode: 201,
      body: mockGroup,
    }).as("createGroup");

    cy.visit("/#/admin/star-systems/system-1", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getSystem");

    // Kattintunk az "Új csoport" gombra
    cy.contains("Új csoport").click();

    // Dialog megjelenik
    cy.contains("Új csoport létrehozása").should("be.visible");

    // Kitöltjük a nevet
    cy.get(
      'input[placeholder*="csoport neve"], input[label*="neve"], .MuiDialog-root input',
    )
      .first()
      .type("Alapok");

    // Mentés
    cy.contains("Létrehozás").click();

    cy.wait("@createGroup");

    // A csoport megjelenik a fán
    cy.contains("Alapok").should("be.visible");
  });

  // ─────────────────────────────────────────────
  // 2. Misszió csoportba helyezése
  // ─────────────────────────────────────────────
  it("should add a standalone mission to a group", () => {
    const systemWithBoth = {
      ...mockSystemEmpty,
      items: [
        {
          type: "GROUP",
          orderIndex: 1,
          group: mockGroup,
          groupMissions: [],
        },
        {
          type: "MISSION",
          orderIndex: 2,
          mission: mockStandaloneMission,
        },
      ],
    };

    // LIFO fix: reload response ELŐSZÖR, initial load UTOLJÁRA times:1-gyel
    cy.intercept("GET", "**/api/star-systems/system-1/with-missions", {
      statusCode: 200,
      body: {
        ...mockSystemEmpty,
        items: [
          {
            type: "GROUP",
            orderIndex: 1,
            group: mockGroup,
            groupMissions: [
              { ...mockStandaloneMission, groupId: "group-1", groupOrder: 1 },
            ],
          },
        ],
      },
    }).as("getSystemUpdated");

    cy.intercept(
      {
        method: "GET",
        url: "**/api/star-systems/system-1/with-missions",
        times: 2,
      },
      { statusCode: 200, body: systemWithBoth },
    ).as("getSystem");

    cy.intercept("POST", "**/api/mission-groups/group-1/missions/mission-1", {
      statusCode: 200,
      body: mockGroup,
    }).as("addMission");

    cy.visit("/#/admin/star-systems/system-1", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getSystem");

    // A csoport "Misszió hozzáadása" gombjára kattintás (MoveToInbox ikon a GROUP-on)
    cy.contains("Alapok")
      .closest("[data-testid=\"group-card\"]")
      .find('[title="Misszió hozzáadása"]')
      .click({ force: true });

    // Dialog megnyílik — választunk a standalone missziók közül
    cy.get(".MuiDialog-root").should("be.visible");
    cy.get(".MuiDialog-root .MuiSelect-select").click();
    cy.get('[role="option"]').contains("Standalone Coding").click();
    cy.contains("Hozzáadás").click();

    cy.wait("@addMission");

    // Misszió megjelenik a csoportban (reload után)
    cy.wait("@getSystemUpdated");
    cy.contains("Standalone Coding").should("be.visible");
  });

  // ─────────────────────────────────────────────
  // 3. FillInBlank szerkesztő — mentés és perzisztencia
  // ─────────────────────────────────────────────
  it("should save and reload FillInBlank definition", () => {
    const mockFibDefinition = {
      missionId: "fib-content-m1",
      templateText: "A víz forráspontja [[blank_1]] Celsius.",
      passThreshold: 50,
      blanks: [
        {
          id: "b1",
          key: "blank_1",
          orderIndex: 0,
          options: [
            { id: "o1", optionText: "100", correct: true, orderIndex: 0 },
            { id: "o2", optionText: "50", correct: false, orderIndex: 1 },
          ],
        },
      ],
    };

    cy.intercept("GET", "**/api/star-systems", {
      statusCode: 200,
      body: [{ id: "system-1", name: "Tatooine" }],
    }).as("getSystems");

    cy.intercept("GET", "**/api/missions/fib-content-m1", {
      statusCode: 200,
      body: {
        id: "fib-content-m1",
        name: "FIB Teszt",
        missionType: "FILL_IN_BLANK",
        difficulty: "EASY",
        starSystemId: "system-1",
        descriptionMarkdown: "",
        templateRepositoryUrl: null,
        orderIndex: 1,
        groupId: null,
        groupOrder: null,
        createdAt: "2024-01-01T10:00:00Z",
      },
    }).as("getMission");

    // LIFO fix: reload response (200) ELŐSZÖR, initial 404 UTOLJÁRA times:1-gyel
    cy.intercept("GET", "**/api/missions/fib-content-m1/fill-in-blank/admin", {
      statusCode: 200,
      body: mockFibDefinition,
    }).as("getFibLoaded");

    cy.intercept(
      {
        method: "GET",
        url: "**/api/missions/fib-content-m1/fill-in-blank/admin",
        times: 1,
      },
      { statusCode: 404, body: {} },
    ).as("getFibNotFound");

    cy.intercept("POST", "**/api/missions/fib-content-m1/fill-in-blank", {
      statusCode: 201,
      body: mockFibDefinition,
    }).as("saveFib");

    cy.visit("/#/admin/missions/fib-content-m1", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getSystems");
    cy.wait("@getMission");

    // Template szöveg megadása (a FILL_IN_BLANK szerkesztő most azonnal, tab nélkül
    // látszik, DE a MissionEditorPage alap-form "Leírás" mezője is mindig a DOM-ban
    // van előtte, ezért a data-cy-vel kell célozni, nem a textarea.first()-tel)
    cy.get('[data-cy="fib-template-text"]')
      .clear()
      .type("A víz forráspontja [[blank_1]] Celsius.");

    // Opciók bevitele a [[blank_1]] panelbe
    cy.get('[data-cy="blank-option-input-blank_1"]').type("100{enter}");
    cy.get('[data-cy="blank-option-input-blank_1"]').type("50{enter}");

    // Mentés gomb (data-cy attribútummal, scrollIntoView a hosszú tartalom miatt)
    cy.get('[data-cy="fib-save"]').scrollIntoView().click();

    cy.wait("@saveFib");

    // Oldal újratöltése — az adat megmaradt
    cy.reload();
    // Reload után előbb az alap adatok töltődnek be
    cy.wait("@getMe");
    cy.wait("@getMission");
    cy.wait("@getFibLoaded");
    cy.contains("[[blank_1]]").should("be.visible");
  });

  // ─────────────────────────────────────────────
  // 4. FIB-ot tartalmazó csoport törlése → sikeres (cascade delete)
  // ─────────────────────────────────────────────
  it("should delete group containing FIB mission successfully (cascade)", () => {
    // LIFO fix: reload (üres) ELŐSZÖR, initial load UTOLJÁRA times:1-gyel
    cy.intercept("GET", "**/api/star-systems/system-1/with-missions", {
      statusCode: 200,
      body: { ...mockSystemEmpty, items: [] },
    }).as("getSystemEmpty");

    cy.intercept(
      {
        method: "GET",
        url: "**/api/star-systems/system-1/with-missions",
        times: 2,
      },
      {
        statusCode: 200,
        body: {
          ...mockSystemEmpty,
          items: [
            {
              type: "GROUP",
              orderIndex: 1,
              group: mockFibGroup,
              groupMissions: [mockFibMission],
            },
          ],
        },
      },
    ).as("getSystem");

    cy.intercept("DELETE", "**/api/mission-groups/fib-group-1", {
      statusCode: 200,
      body: {},
    }).as("deleteGroup");

    cy.visit("/#/admin/star-systems/system-1", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getSystem");

    // FIB Csoport látható
    cy.contains("FIB Csoport").should("be.visible");

    // Törlés gombra kattintás
    cy.contains("FIB Csoport")
      .closest("[data-testid=\"group-card\"]")
      .find("[aria-label='Törlés']")
      .first()
      .click({ force: true });

    cy.wait("@deleteGroup");

    // Csoport eltűnik (reload után)
    cy.wait("@getSystemEmpty");
    cy.contains("FIB Csoport").should("not.exist");
  });

  // ─────────────────────────────────────────────
  // 5. Sorrend csere — ↑/↓ gomb
  // ─────────────────────────────────────────────
  it("should reorder groups with up/down buttons", () => {
    const group2 = {
      ...mockGroup,
      id: "group-2",
      name: "Haladók",
      orderIndex: 2,
    };

    // LIFO fix: reload response ELŐSZÖR, initial load UTOLJÁRA times:1-gyel
    cy.intercept("GET", "**/api/star-systems/system-1/with-missions", {
      statusCode: 200,
      body: {
        ...mockSystemEmpty,
        items: [
          { type: "GROUP", orderIndex: 1, group: group2, groupMissions: [] },
          { type: "GROUP", orderIndex: 2, group: mockGroup, groupMissions: [] },
        ],
      },
    }).as("getSystemReordered");

    cy.intercept(
      {
        method: "GET",
        url: "**/api/star-systems/system-1/with-missions",
        times: 2,
      },
      {
        statusCode: 200,
        body: {
          ...mockSystemEmpty,
          items: [
            {
              type: "GROUP",
              orderIndex: 1,
              group: mockGroup,
              groupMissions: [],
            },
            { type: "GROUP", orderIndex: 2, group: group2, groupMissions: [] },
          ],
        },
      },
    ).as("getSystem");

    cy.intercept("POST", "**/api/star-systems/system-1/reorder-items", {
      statusCode: 200,
      body: {
        updated: [
          { id: "group-1", orderIndex: 2 },
          { id: "group-2", orderIndex: 1 },
        ],
      },
    }).as("reorder");

    cy.visit("/#/admin/star-systems/system-1", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getMe");
    cy.wait("@getSystem");

    // Mindkét csoport látható, Alapok van felül
    cy.contains("Alapok").should("be.visible");
    cy.contains("Haladók").should("be.visible");

    // Az első csoport "Le" gombjára kattintunk
    cy.contains("Alapok")
      .closest("[data-testid=\"group-card\"]")
      .find("[aria-label='Le']")
      .first()
      .click({ force: true });

    cy.wait("@reorder");

    // Haladók most felül van
    cy.contains("Haladók").should("be.visible");
  });
});
