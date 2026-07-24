import { expect, test } from '@playwright/test'

import { loginByApi, webBaseUrl } from './support/api'

test('@smoke @platform-scale-s04-m4 legacy knowledge commands only return the Yjs upgrade contract', async ({ page, request }) => {
  const session = await loginByApi(request)
  await page.goto('/login')
  const socketUrl = new URL(process.env.COLLA_E2E_WS_BASE_URL ?? webBaseUrl)
  socketUrl.protocol = socketUrl.protocol === 'https:' ? 'wss:' : 'ws:'
  socketUrl.pathname = '/ws/events'
  socketUrl.search = new URLSearchParams({ token: session.accessToken }).toString()

  const result = await page.evaluate((url) => new Promise<{
    notice: { type?: string; protocol?: string; endpoint?: string; command?: string }
    readyState: number
  }>((resolve, reject) => {
    const socket = new WebSocket(url)
    const timer = window.setTimeout(() => reject(new Error('upgrade notice timed out')), 15_000)
    socket.addEventListener('message', (event) => {
      const frame = JSON.parse(String(event.data)) as { type?: string; protocol?: string; endpoint?: string; command?: string }
      if (frame.type === 'connection.ready') {
        socket.send(JSON.stringify({
          type: 'knowledge.content.update',
          workspaceId: '00000000-0000-0000-0000-000000000001',
          itemId: '00000000-0000-0000-0000-000000000002',
          update: 'must-not-be-dispatched',
        }))
      }
      if (frame.type === 'protocol.upgrade_required') {
        window.clearTimeout(timer)
        const readyState = socket.readyState
        socket.close()
        resolve({ notice: frame, readyState })
      }
    })
    socket.addEventListener('error', () => {
      window.clearTimeout(timer)
      reject(new Error('WebSocket handshake failed'))
    }, { once: true })
  }), socketUrl.toString())

  expect(result.notice).toEqual({
    type: 'protocol.upgrade_required',
    protocol: 'colla-yjs-v1',
    endpoint: '/collaboration',
    command: 'update',
  })
  expect(result.readyState).toBe(WebSocket.OPEN)
})
