// Shared helpers for integration tests (real backend)

export const API = () =>
  (Cypress.env("API_URL") as string) ?? "http://localhost:8080/api";

export const adminCreds = () => ({
  username: (Cypress.env("ADMIN_USERNAME") as string) ?? "admin",
  password: (Cypress.env("ADMIN_PASSWORD") as string) ?? "Admin1234!",
});

export const cadetCreds = () => ({
  username: (Cypress.env("CADET_USERNAME") as string) ?? "cadet",
  password: (Cypress.env("CADET_PASSWORD") as string) ?? "Cadet1234!",
});

/** POST /auth/login → returns JWT token string */
export const apiLogin = (username: string, password: string) =>
  cy
    .request({ method: "POST", url: `${API()}/auth/login`, body: { username, password } })
    .then((res) => res.body.token as string);

/** Visit a page with a real JWT token pre-loaded into localStorage */
export const visitWithToken = (path: string, token: string) =>
  cy.visit(path, { onBeforeLoad: (win) => win.localStorage.setItem("token", token) });

/** Unique name prefix for test data so parallel runs don't conflict */
export const uid = () => `cy-${Date.now()}`;
