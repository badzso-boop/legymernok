import { createMockJwt } from "../support/utils";

describe("Admin Star System Management (Mocked Backend)", () => {
  const token = createMockJwt(["ROLE_ADMIN"]);

  beforeEach(() => {
    cy.intercept("GET", "**/api/star-systems", {
      statusCode: 200,
      body: [
        {
          id: "system-1",
          name: "Coruscant",
          description: "City planet",
          createdAt: "2024-01-01T10:00:00Z",
          updatedAt: "2024-01-01T10:00:00Z",
        },
      ],
    }).as("getSystems");
  });

  it("should list star systems", () => {
    cy.visit("/#/admin/star-systems", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getSystems");
    cy.contains("Coruscant").should("be.visible");
    cy.contains("City planet").should("be.visible");
  });

  it("should create a new star system", () => {
    cy.intercept("POST", "**/api/star-systems", {
      statusCode: 200,
      body: { id: "new-1", name: "Dagobah" },
    }).as("createSystem");

    cy.visit("/#/admin/star-systems/new", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.get('input[name="name"]').type("Dagobah");
    cy.get('textarea[name="description"]').type("Swamp planet");
    cy.get("button").contains("Mentés").click();

    cy.wait("@createSystem").its("request.body").should("deep.include", {
      name: "Dagobah",
      description: "Swamp planet",
    });

    // After save, it should navigate back to list
    cy.url().should("include", "/admin/star-systems");
  });

  it("should edit an existing star system", () => {
    // Mock the specific system fetch
    cy.intercept("GET", "**/api/star-systems/system-1/with-missions", {
      statusCode: 200,
      body: {
        id: "system-1",
        name: "Coruscant",
        description: "City planet",
        iconUrl: "",
        missions: [],
      },
    }).as("getSystemDetail");

    cy.intercept("PUT", "**/api/star-systems/system-1", {
      statusCode: 200,
      body: {},
    }).as("updateSystem");

    cy.visit("/#/admin/star-systems/system-1", {
      onBeforeLoad(win) {
        win.localStorage.setItem("token", token);
      },
    });

    cy.wait("@getSystemDetail");

    // Check pre-filled values
    cy.get('input[name="name"]').should("have.value", "Coruscant");

    // Update value
    cy.get('input[name="name"]').clear().type("Coruscant Updated");
    cy.get("button").contains("Mentés").click();

    cy.wait("@updateSystem").its("request.body").should("deep.include", {
      name: "Coruscant Updated",
    });
  });
});
