import { expect, test, type APIRequestContext, type APIResponse } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type TypeSummary = { id: string; typeKey: string }
type Item = { id: string; version: number; title: string }
type BoardAction = {
  kind: 'state' | 'node'
  actionKey: string
  label: string
  fromStateKey?: string
  taskId?: string
  expectedInstanceVersion: number
}
type BoardCard = {
  workItemId: string
  title: string
  workItemVersion: number
  columnKey: string
  swimlaneKey: string
  rank: number
  orderVersion: number
  moveActions: BoardAction[]
}
type Board = {
  evaluatedCandidates: number
  candidateBoundReached: boolean
  columns: Array<{
    column: { key: string; label: string; wipLimit: number }
    visibleCount: number
    lanes: Array<{ key: string; cards: BoardCard[] }>
  }>
}
type Member = { id: string; userId: string }

const columns = [
  { key: 'open', label: '待处理', wipLimit: 8, moveKind: 'state', moveActionKey: 'reopen' },
  { key: 'in_progress', label: '处理中', wipLimit: 5, moveKind: 'state', moveActionKey: 'start_progress' },
  { key: 'done', label: '已完成', wipLimit: 0, moveKind: 'state', moveActionKey: 'complete' },
  { key: 'canceled', label: '已取消', wipLimit: 0, moveKind: 'state', moveActionKey: 'terminate' },
]

test.describe('PROJECT-PLATFORM-S14 M1', () => {
  test('board grouping, exact moves and responsive recovery close on isolated six identities @route-final', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    page.setDefaultTimeout(25_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request, `${apiBaseUrl}/auth/me`, enterprise,
    )
    const suffix = `s14m1_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S14 Board Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S14 Board Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S14 Board Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S14 Board Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S14 Board Outsider')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const spaceAdmin = await loginByApi(request, adminIdentity.username, adminIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let spaceId: string | undefined
    let otherSpaceId: string | undefined

    try {
      spaceId = await createSpace(request, owner, suffix, 'primary')
      await addMember(request, owner, spaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, spaceId, memberIdentity.id, 'member')
      await addMember(request, owner, spaceId, guestIdentity.id, 'guest')
      const typeId = await projectType(request, owner, spaceId)
      await validateAndPublish(request, owner, spaceId, typeId, `${suffix}-primary`)
      const longTitle = `S14 看板 ${'超长标题'.repeat(32)}`
      const first = await createItem(
        request, owner, spaceId, typeId, longTitle, `${suffix}-item-a`,
      )
      const second = await createItem(
        request, owner, spaceId, typeId, 'S14 键盘移动卡片', `${suffix}-item-b`,
      )
      const viewKey = `type-${typeId}`
      const body = boardRequest(typeId, viewKey)

      for (const session of [owner, spaceAdmin, member, guest]) {
        const board = await postJson<Board>(
          request,
          `${apiBaseUrl}/project-spaces/${spaceId}/work-item-boards:render`,
          session,
          body,
          `${suffix}-matrix-${session.accessToken.slice(-6)}`,
        )
        expect(board.columns).toHaveLength(4)
        expect(board.columns.reduce((sum, column) => sum + column.visibleCount, 0)).toBe(2)
        expect(board.evaluatedCandidates).toBe(2)
        expect(board.candidateBoundReached).toBeFalsy()
        expect(board.columns.find((column) => column.column.key === 'done')?.visibleCount).toBe(0)
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.post(
          `${apiBaseUrl}/project-spaces/${spaceId}/work-item-boards:render`,
          { headers: bearer(session), data: body },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(longTitle)
      }

      otherSpaceId = await createSpace(request, owner, suffix, 'other')
      const otherTypeId = await projectType(request, owner, otherSpaceId)
      await validateAndPublish(request, owner, otherSpaceId, otherTypeId, `${suffix}-other`)
      const otherItem = await createItem(
        request, owner, otherSpaceId, otherTypeId, 'S14 跨空间隐藏卡片', `${suffix}-other-item`,
      )

      await savePreference(request, owner, spaceId, viewKey, `${suffix}-owner-pref`)
      const initial = await renderBoard(request, owner, spaceId, body, `${suffix}-initial`)
      const firstCard = card(initial, first.id)
      const start = firstCard.moveActions.find((action) => action.actionKey === 'start_progress')
      expect(start).toBeTruthy()
      if (!start) throw new Error('start_progress action missing')
      const moveA = moveBody(firstCard, start, 'in_progress', `${suffix}-move-a`)
      const moveB = moveBody(firstCard, start, 'in_progress', `${suffix}-move-b`)
      const attempts = await Promise.all([
        rawMove(request, owner, spaceId, viewKey, first.id, moveA),
        rawMove(request, owner, spaceId, viewKey, first.id, moveB),
      ])
      const attemptDiagnostics = await Promise.all(attempts.map(async (response) => ({
        status: response.status(),
        body: await response.text(),
      })))
      expect(
        attempts.filter((response) => response.ok()),
        JSON.stringify(attemptDiagnostics),
      ).toHaveLength(1)
      const loser = attempts.find((response) => !response.ok())
      expect(loser?.status()).toBe(409)
      const winner = attempts.find((response) => response.ok())
      if (!winner) throw new Error('concurrent move winner missing')
      const winnerBody = winner === attempts[0] ? moveA : moveB
      const winnerResult = await winner.json()
      const replay = await rawMove(
        request, owner, spaceId, viewKey, first.id, winnerBody,
      )
      expect(replay.ok(), await replay.text()).toBeTruthy()
      expect(await replay.json()).toMatchObject({
        ...winnerResult,
        replayed: true,
      })
      const afterRace = await renderBoard(request, owner, spaceId, body, `${suffix}-after-race`)
      expect(allCards(afterRace).filter((candidate) => candidate.workItemId === first.id)).toHaveLength(1)
      expect(card(afterRace, first.id).columnKey).toBe('in_progress')

      const crossSpace = await rawMove(
        request,
        owner,
        spaceId,
        viewKey,
        otherItem.id,
        {
          ...moveBody(firstCard, start, 'in_progress', `${suffix}-cross-space`),
          expectedWorkItemVersion: otherItem.version,
        },
      )
      expect([403, 404]).toContain(crossSpace.status())
      expect(await crossSpace.text()).not.toContain(otherItem.title)

      const memberBoard = await renderBoard(
        request, member, spaceId, body, `${suffix}-member-before-revoke`,
      )
      const memberCard = card(memberBoard, second.id)
      await savePreference(request, member, spaceId, viewKey, `${suffix}-member-pref`)
      const members = await getJson<Member[]>(
        request, `${apiBaseUrl}/project-spaces/${spaceId}/members`, owner,
      )
      const memberRecord = members.find((candidate) => candidate.userId === memberIdentity.id)
      expect(memberRecord).toBeTruthy()
      if (!memberRecord) throw new Error('member record missing')
      const removed = await request.delete(
        `${apiBaseUrl}/project-spaces/${spaceId}/members/${memberRecord.id}`,
        { headers: bearer(owner) },
      )
      expect(removed.ok(), await removed.text()).toBeTruthy()
      const memberAction = memberCard.moveActions.find(
        (action) => action.actionKey === 'start_progress',
      )
      expect(memberAction).toBeTruthy()
      if (!memberAction) throw new Error('member action missing')
      const revokedMove = await rawMove(
        request,
        member,
        spaceId,
        viewKey,
        second.id,
        moveBody(memberCard, memberAction, 'in_progress', `${suffix}-revoked-move`),
      )
      expect([403, 404]).toContain(revokedMove.status())
      const revokedRender = await request.post(
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-boards:render`,
        { headers: bearer(member), data: body },
      )
      expect([403, 404]).toContain(revokedRender.status())

      await installSession(page, owner)
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(`/project-spaces/${spaceId}/work-items?typeId=${typeId}`)
      await page.getByLabel('工作项视图模式').getByText('看板').click()
      const board = page.getByTestId('project-work-item-board')
      await expect(board).toBeVisible()
      await expect(board.locator('.project-work-item-board-card')).toHaveCount(2)
      await expect(board).toContainText(longTitle)

      const wipInput = page.locator('.project-work-item-board-wip').first()
      await page.context().setOffline(true)
      await wipInput.fill('7')
      await page.getByRole('button', { name: '保存偏好' }).click()
      await page.context().setOffline(false)
      await expect(page.locator('.project-work-item-board-wip').first()).toHaveValue('7')
      await page.getByRole('button', { name: '保存偏好' }).click()
      await expect(page.getByText('视图偏好已保存')).toBeVisible()

      const keyboardCard = board.locator('.project-work-item-board-card', {
        hasText: second.title,
      })
      await keyboardCard.focus()
      await keyboardCard.press('ArrowRight')
      await expect(page.getByText('看板位置已更新')).toBeVisible()
      await expect(page.getByTestId('board-column-in_progress')).toContainText(second.title)

      await page.getByLabel('看板泳道').click()
      await page.getByText('按我的参与角色').click()
      await expect(board.locator('[data-testid^="board-lane-"]').first()).toBeVisible()
      expect(await board.locator('[data-testid^="board-lane-"]').count()).toBeGreaterThanOrEqual(4)
      await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0)

      for (const width of [1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(board).toBeVisible()
        await page.evaluate(() => new Promise<void>((resolve) => {
          requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
        }))
        const horizontalOverflow = await page.evaluate(() => {
          const viewportWidth = document.documentElement.clientWidth
          const offenders = Array.from(document.querySelectorAll<HTMLElement>('body *'))
            .map((element) => {
              const rect = element.getBoundingClientRect()
              return {
                tag: element.tagName,
                classes: element.className?.toString().slice(0, 160) ?? '',
                left: Math.round(rect.left),
                right: Math.round(rect.right),
                width: Math.round(rect.width),
                scrollWidth: element.scrollWidth,
              }
            })
            .filter((item) => item.right > viewportWidth + 1 || item.left < -1)
            .sort((left, right) => right.right - left.right)
            .slice(0, 8)
          const board = document.querySelector<HTMLElement>('.project-work-item-board')
          const layout = Array.from({ length: 7 }, (_, index) => {
            let element: HTMLElement | null = board
            for (let depth = 0; depth < index; depth += 1) element = element?.parentElement ?? null
            if (!element) return null
            const rect = element.getBoundingClientRect()
            return {
              tag: element.tagName,
              classes: element.className?.toString().slice(0, 160) ?? '',
              left: Math.round(rect.left),
              right: Math.round(rect.right),
              width: Math.round(rect.width),
              clientWidth: element.clientWidth,
              scrollWidth: element.scrollWidth,
              overflowX: getComputedStyle(element).overflowX,
            }
          }).filter(Boolean)
          return {
            delta: document.documentElement.scrollWidth - viewportWidth,
            offenders,
            layout,
          }
        })
        expect(
          horizontalOverflow.delta,
          JSON.stringify({ offenders: horizontalOverflow.offenders, layout: horizontalOverflow.layout }),
        )
          .toBeLessThanOrEqual(1)
      }
      await page.screenshot({ path: testInfo.outputPath('s14-board-820.png'), fullPage: true })

      await installSession(page, guest)
      await page.goto(`/project-spaces/${spaceId}/work-items?typeId=${typeId}`)
      await page.getByLabel('工作项视图模式').getByText('看板').click()
      await expect(page.getByTestId('project-work-item-board')).toBeVisible()
      await expect(page.getByTestId('project-work-item-board')).toContainText('流程动作会在服务端重新鉴权')
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      for (const id of [spaceId, otherSpaceId]) {
        if (id) {
          await request.post(`${apiBaseUrl}/project-spaces/${id}/settings/archive`, {
            headers: bearer(owner),
          }).catch(() => undefined)
        }
      }
      for (const identity of [
        adminIdentity, memberIdentity, guestIdentity, outsiderIdentity, ownerIdentity,
      ]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterprise),
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})

function boardRequest(typeId: string, viewKey: string) {
  return {
    schemaVersion: 1,
    viewKey,
    columnField: 'state',
    swimlaneField: null,
    columns,
    query: {
      schemaVersion: 1,
      typeId,
      filter: null,
      sorts: [{ field: 'updatedAt', direction: 'desc', nulls: 'last' }],
      group: null,
      select: ['id', 'displayKey', 'title', 'status', 'state'],
      limit: 100,
      cursor: null,
    },
  }
}

function allCards(board: Board) {
  return board.columns.flatMap((column) => column.lanes.flatMap((lane) => lane.cards))
}

function card(board: Board, workItemId: string) {
  const result = allCards(board).find((candidate) => candidate.workItemId === workItemId)
  if (!result) throw new Error(`board card ${workItemId} missing`)
  return result
}

function moveBody(card: BoardCard, action: BoardAction, targetColumnKey: string, requestId: string) {
  return {
    requestId,
    expectedWorkItemVersion: card.workItemVersion,
    expectedOrderVersion: card.orderVersion,
    targetColumnKey,
    targetSwimlaneKey: card.swimlaneKey,
    rank: 1024,
    kind: action.kind,
    actionKey: action.actionKey,
    fromStateKey: action.fromStateKey ?? null,
    taskId: action.taskId ?? null,
    nodeOperation: action.kind === 'node' ? action.actionKey : null,
    expectedInstanceVersion: action.expectedInstanceVersion,
    decision: null,
    fieldPatch: null,
  }
}

async function renderBoard(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  body: unknown,
  requestId: string,
) {
  return postJson<Board>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/work-item-boards:render`,
    session,
    body,
    requestId,
  )
}

async function savePreference(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  viewKey: string,
  requestId: string,
) {
  const response = await request.put(
    `${apiBaseUrl}/project-spaces/${spaceId}/work-item-boards/${viewKey}/preference`,
    {
      headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
      data: {
        requestId,
        expectedVersion: 0,
        columnField: 'state',
        swimlaneField: null,
        columns,
      },
    },
  )
  expect(response.ok(), await response.text()).toBeTruthy()
}

function rawMove(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  viewKey: string,
  itemId: string,
  data: Record<string, unknown>,
): Promise<APIResponse> {
  return request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/work-item-boards/${viewKey}/items/${itemId}:move`,
    {
      headers: {
        ...bearer(session),
        'X-Colla-Request-Id': String(data.requestId),
      },
      data,
    },
  )
}

async function projectType(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
) {
  const types = await getJson<{ items: TypeSummary[] }>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`,
    owner,
  )
  const taskType = types.items.find((candidate) => candidate.typeKey === 'task')
  expect(taskType).toBeTruthy()
  if (!taskType) throw new Error('task preset missing')
  return taskType.id
}

async function validateAndPublish(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeId: string,
  suffix: string,
) {
  const base = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}`
  let draft = await getJson<{ aggregateVersion: number }>(request, `${base}/draft`, owner)
  draft = await postJson(
    request,
    `${base}/draft:validate`,
    owner,
    { expectedAggregateVersion: draft.aggregateVersion },
    `${suffix}-validate`,
  )
  await postJson(
    request,
    `${base}/draft:publish`,
    owner,
    { expectedDraftAggregateVersion: draft.aggregateVersion, breakingConfirmed: false },
    `${suffix}-publish`,
  )
}

async function createItem(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  typeId: string,
  title: string,
  requestId: string,
) {
  return postJson<Item>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/work-items`,
    session,
    { typeId, title, fieldValues: {} },
    requestId,
  )
}

async function createSpace(
  request: APIRequestContext,
  owner: E2eSession,
  suffix: string,
  kind: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s14-m1-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S14 看板 ${kind} ${suffix}`,
      visibility: 'private',
    },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

async function addMember(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  userId: string,
  roleKey: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s14-m1-member-${userId}` },
    data: { userId, roleKey },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
}

async function createIdentity(
  request: APIRequestContext,
  administrator: E2eSession,
  username: string,
  displayName: string,
) {
  const password = 'member123456'
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(administrator),
    data: {
      username,
      password,
      displayName,
      email: `${username}@example.com`,
      roleCode: 'member',
    },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return { ...(await response.json() as Omit<Identity, 'password'>), password }
}

async function getJson<T>(request: APIRequestContext, url: string, session: E2eSession) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url} failed: ${await response.text()}`).toBeTruthy()
  return await response.json() as T
}

async function postJson<T>(
  request: APIRequestContext,
  url: string,
  session: E2eSession,
  data: unknown,
  requestId: string,
) {
  const response = await request.post(url, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
    data,
  })
  expect(response.ok(), `POST ${url} failed: ${await response.text()}`).toBeTruthy()
  return await response.json() as T
}
