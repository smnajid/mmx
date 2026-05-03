import { defineConfig } from 'cypress';

export default defineConfig({
  e2e: {
    baseUrl: 'http://localhost:4200',
    supportFile: false,
    video: false,
    specPattern: 'cypress/e2e/**/*.cy.ts',
    env: {
      apiBaseUrl: 'http://localhost:8080',
    },
  },
});
