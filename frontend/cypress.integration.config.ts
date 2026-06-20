import { defineConfig } from "cypress";

export default defineConfig({
  e2e: {
    baseUrl: "http://localhost:3000",
    viewportWidth: 1600,
    viewportHeight: 900,
    supportFile: false,
    setupNodeEvents(on, config) {},
    specPattern: "cypress/e2e/integration/*.cy.ts",
    env: {
      API_URL: "http://localhost:8080/api",
    },
  },
});
