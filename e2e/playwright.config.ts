import { defineConfig, devices } from '@playwright/test';

// Runs against the throwaway stack from .claude/launch.json:
//   "api (testcontainers db)" on 8081 and "frontend (testcontainers api)" on 4201.
// Start both first; see README.md.
export default defineConfig({
  testDir: './tests',
  workers: 1,
  retries: 0,
  reporter: [['list']],
  outputDir: './test-results',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4201',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
