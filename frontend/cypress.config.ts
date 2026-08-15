import { defineConfig } from "cypress";

export default defineConfig({
  e2e: {
    baseUrl: "http://localhost:5173",
    viewportWidth: 1600,
    viewportHeight: 900,
    supportFile: false,
    setupNodeEvents(on, config) {},
    // Alapértelmezetten csak a mockolt tesztek futnak (CI-kompatibilis)
    specPattern: "cypress/e2e/*.cy.ts",
  },
});
