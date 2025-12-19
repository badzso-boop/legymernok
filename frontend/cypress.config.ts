import { defineConfig } from "cypress";

export default defineConfig({
    e2e: {
        baseUrl: 'http://localhost:5173', // A frontend URL-je
        setupNodeEvents(on, config) {
            // implement node event listeners here
        },
        supportFile: false, // Egyszerűsítés végett most kikapcsoljuk
    },
});