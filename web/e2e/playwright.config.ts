import { defineConfig } from '@playwright/test'

const suite = process.env.COLLA_E2E_SUITE ?? 'smoke'
const suitePattern = suite === 'route-final'
  ? /@route-final/
  : suite === 'pilot-m9'
    ? /@pilot-m9/
    : suite === 'pilot-m10'
      ? /@pilot-m10/
      : /@smoke/

export default defineConfig({
  testDir: '.',
  testMatch: '**/*.spec.ts',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  outputDir: 'test-results/artifacts',
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL: process.env.COLLA_E2E_WEB_BASE_URL ?? 'http://127.0.0.1:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  grep: suitePattern,
  reporter: [['list'], ['html', { outputFolder: 'test-results/report', open: 'never' }]],
})
