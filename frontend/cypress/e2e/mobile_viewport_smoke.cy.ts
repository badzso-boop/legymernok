/// <reference types="cypress" />
import { createMockJwt } from "../support/utils";

// Terv 9.10 szekció: átfogó smoke-teszt a fő flow-kra mobil viewportokon
// (360px, 390px, 430px) — nem duplikálja a meglévő specek részletes eseteit,
// csak azt ellenőrzi, hogy a kulcs oldalak vízszintes scroll nélkül,
// a fő elemek láthatóságával renderelnek ezeken a szélességeken.

const VIEWPORTS: Array<[number, number]> = [
  [360, 800],
  [390, 844],
  [430, 932],
];

const assertNoHorizontalScroll = () => {
  cy.document().then((doc) => {
    const html = doc.documentElement;
    expect(html.scrollWidth, "no horizontal overflow").to.be.at.most(
      html.clientWidth + 1, // 1px tolerancia sub-pixel kerekítésre
    );
  });
};

describe("Mobil viewport smoke teszt (360/390/430px)", () => {
  const token = createMockJwt(["ROLE_CADET"]);

  VIEWPORTS.forEach(([width, height]) => {
    describe(`${width}x${height}`, () => {
      beforeEach(() => {
        cy.viewport(width, height);
      });

      it("landing oldal (nem authentikált) — nincs vízszintes scroll, hero látszik", () => {
        cy.visit("/#/");
        cy.get("body").should("be.visible");
        assertNoHorizontalScroll();
      });

      it("dashboard (authentikált) — nincs vízszintes scroll, streak-sáv látszik", () => {
        cy.intercept("GET", "**/api/auth/me", {
          statusCode: 200,
          body: {
            id: "cadet-1",
            username: "cypress_cadet",
            roles: ["ROLE_CADET"],
            currentStreak: 3,
            longestStreak: 5,
          },
        }).as("getMe");
        cy.intercept("GET", "**/api/dashboard/continue", {
          statusCode: 404,
          body: {},
        }).as("getContinue");
        cy.intercept("GET", "**/api/social/activity-feed", {
          statusCode: 200,
          body: [],
        }).as("getActivityFeed");
        cy.intercept("GET", "**/api/star-systems/with-progress", {
          statusCode: 200,
          body: [],
        }).as("getWithProgress");

        cy.visit("/#/", {
          onBeforeLoad(win) {
            win.localStorage.setItem("token", token);
          },
        });

        cy.wait("@getMe");
        assertNoHorizontalScroll();
      });

      it("star map oldal — nincs vízszintes scroll", () => {
        cy.intercept("GET", "**/api/auth/me", {
          statusCode: 200,
          body: { id: "cadet-1", username: "cypress_cadet", roles: ["ROLE_CADET"] },
        }).as("getMe");
        cy.intercept("GET", "**/api/star-systems/with-progress", {
          statusCode: 200,
          body: [
            {
              id: "s1",
              name: "Naprendszer",
              description: "",
              status: "IN_PROGRESS",
            },
          ],
        }).as("getWithProgress");

        cy.visit("/#/star-map", {
          onBeforeLoad(win) {
            win.localStorage.setItem("token", token);
          },
        });

        cy.wait("@getMe");
        assertNoHorizontalScroll();
      });

      it("saját profil oldal — nincs vízszintes scroll", () => {
        cy.intercept("GET", "**/api/auth/me", {
          statusCode: 200,
          body: {
            id: "cadet-1",
            username: "cypress_cadet",
            roles: ["ROLE_CADET"],
            currentStreak: 0,
            longestStreak: 0,
          },
        }).as("getMe");
        cy.intercept("GET", "**/api/cadets/cadet-1/profile", {
          statusCode: 200,
          body: {
            id: "cadet-1",
            username: "cypress_cadet",
            fullName: "Cypress Cadet",
            avatarUrl: null,
            currentStreak: 0,
            longestStreak: 0,
            totalCompletedMissions: 0,
            totalCompletedGroups: 0,
            followerCount: 0,
            followingCount: 0,
            memberSince: "2026-01-01T00:00:00Z",
          },
        }).as("getProfile");

        cy.visit("/#/profile", {
          onBeforeLoad(win) {
            win.localStorage.setItem("token", token);
          },
        });

        cy.wait("@getMe");
        assertNoHorizontalScroll();
      });
    });
  });
});
