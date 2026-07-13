import type { Page, TestInfo } from '@playwright/test'

type NetworkFailure = {
  status: number
  method: string
  url: string
}

export function installFailureEvidence(page: Page, testInfo: TestInfo) {
  const consoleErrors: string[] = []
  const failedResponses: NetworkFailure[] = []

  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text())
    }
  })
  page.on('response', (response) => {
    if (response.url().includes('/api/') && response.status() >= 400) {
      failedResponses.push({
        status: response.status(),
        method: response.request().method(),
        url: response.url(),
      })
    }
  })

  return async () => {
    if (testInfo.status === testInfo.expectedStatus) {
      return
    }
    await testInfo.attach('browser-evidence.json', {
      body: Buffer.from(JSON.stringify({ consoleErrors, failedResponses }, null, 2)),
      contentType: 'application/json',
    })
    await testInfo.attach('browser-url.txt', {
      body: Buffer.from(page.url()),
      contentType: 'text/plain',
    })
    await page.screenshot({ path: testInfo.outputPath('failure-page.png'), fullPage: true }).catch(() => undefined)
  }
}
