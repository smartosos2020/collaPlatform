import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

import {
  apiBaseUrl,
  bearer,
  installSession,
  loginByApi,
  type E2eSession,
} from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type ProjectSpaceFixture = {
  id: string
  name: string
}
type JsonRecord = Record<string, unknown>
type ConfigurationDraftFixture = {
  aggregateVersion: number
  snapshot: JsonRecord
}

test.describe('PROJECT-PLATFORM-S21-M7 reopened real isolated requirement flow', () => {
  test(
    'publishes requirement v2 from legacy partial v1 and closes a requirement through terminal done @smoke',
    async ({ page, request }, testInfo) => {
      test.setTimeout(240_000)
      page.setDefaultTimeout(20_000)
      requireIsolatedIdentityFixture()

      const owner = await loginByApi(request)
      const suffix = `s21m7reopened_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
      let space: ProjectSpaceFixture | undefined

      try {
        space = await createSpaceFixture(request, owner, suffix)
        await installSession(page, owner)
        await page.setViewportSize({ width: 1440, height: 900 })

        await page.goto(`/project-spaces/${space.id}`)
        await dismissAutomaticOnboarding(page, space.id)
        const requirementEntry = page.getByTestId(
          'project-space-work-entry-requirement',
        )
        await expect(requirementEntry).toContainText('待发布配置')
        await expect(requirementEntry).toContainText('requirement')

        let unreadyCreateFormRequests = 0
        page.on('request', (request) => {
          if (request.url().includes('/work-items/types/')
            && request.url().endsWith('/create-form')) {
            unreadyCreateFormRequests += 1
          }
        })
        const primaryNavigation = page.getByRole('navigation', { name: '空间导航' })
        await primaryNavigation.getByRole('button', {
          name: '工作项',
          exact: true,
        }).click()
        const typeFilter = page.getByLabel('按工作项类型筛选')
        await typeFilter.click()
        await page.getByTitle('需求 · 待发布配置', { exact: true }).click()
        await page.getByRole('button', {
          name: /新建工作项$/,
        }).click()
        const unreadyDialog = page.getByRole('dialog', { name: '新建需求' })
        await expect(unreadyDialog).toContainText('该类型尚未发布可用配置')
        await expect(unreadyDialog).toContainText('请先完成配置校验并发布版本')
        await expect(unreadyDialog.getByRole('button', {
          name: /^创\s*建$/,
        })).toBeDisabled()
        expect(unreadyCreateFormRequests).toBe(0)
        await unreadyDialog.getByRole('button', {
          name: /^取\s*消$/,
        }).click()
        await primaryNavigation.getByRole('button', {
          name: '概览',
          exact: true,
        }).click()

        await requirementEntry.getByRole('button', {
          name: '去配置需求（requirement）',
          exact: true,
        }).click()
        await expect(page).toHaveURL(new RegExp(
          `/project-spaces/${space.id}/types/[^?]+`
            + '\\?panel=configuration-draft&source=overview$',
        ))
        const requirementTypeId = await selectedTypeId(page, space.id)
        await expect(page.getByTestId('project-space-types-secondary-tabs')
          .getByRole('tab', {
            name: '配置发布',
            exact: true,
          })).toHaveAttribute('aria-selected', 'true')

        const publicationPanel = page.getByRole('region', {
          name: '配置草稿状态',
        })
        await expect(publicationPanel).toBeVisible()
        await expect(publicationPanel).toContainText('legacy partial')
        await expect(publicationPanel.getByRole('listitem').filter({
          hasText: 'v1',
        })).toContainText('legacy partial')

        const templatePanel = page.getByRole('region', {
          name: '配置模板',
        })
        const templateSelect = templatePanel.getByLabel('选择配置模板')
        await expect(templateSelect).toBeVisible()
        await templateSelect.click()
        await page.getByTitle('需求 · v1', { exact: true }).click()
        const installResponse = page.waitForResponse(response =>
          response.url().endsWith(
            `/configuration/types/${requirementTypeId}/template-installation`,
          )
          && response.request().method() === 'POST')
        await templatePanel.getByRole('button', {
          name: /安装$/,
        }).click()
        await page.getByRole('dialog').getByRole('button', {
          name: '安装到草稿',
          exact: true,
        }).click()
        expect((await installResponse).ok()).toBeTruthy()
        await expect(page.getByText('模板已复制到配置草稿')).toBeVisible()
        await expect(templatePanel).toContainText('已关联')

        const stateFlowEditor = page.getByTestId('work-item-state-flow-editor')
        await expect(stateFlowEditor).toContainText('轻量状态流配置')
        await expect(stateFlowEditor.getByRole('tab', {
          name: '状态 4',
          exact: true,
        })).toBeVisible()
        await expect(stateFlowEditor.getByRole('tab', {
          name: '动作 5',
          exact: true,
        })).toBeVisible()
        await expect(stateFlowEditor.getByRole('tab', {
          name: '转换 6',
          exact: true,
        })).toBeVisible()

        await stateFlowEditor.getByRole('button', {
          name: /新增状态$/,
        }).click()
        await expect(stateFlowEditor.getByRole('tab', {
          name: '状态 5',
          exact: true,
        })).toBeVisible()
        const actionTab = stateFlowEditor.getByRole('tab', {
          name: '动作 5',
          exact: true,
        })
        await actionTab.click()
        await stateFlowEditor.locator('.ant-tabs-tabpane-active .ant-collapse-item')
          .first()
          .locator('.ant-collapse-title')
          .click()

        await expect(publicationPanel.getByRole('button', {
          name: /校验配置$/,
        })).toBeDisabled()
        await expect(publicationPanel.getByRole('button', {
          name: /发布版本$/,
        })).toBeDisabled()
        await expect(publicationPanel).toContainText('状态流有未保存修改')

        const externalDraftResponse = await request.get(
          `${apiBaseUrl}/project-spaces/${space.id}/configuration/types/${requirementTypeId}/draft`,
          { headers: bearer(owner) },
        )
        expect(externalDraftResponse.ok(), await externalDraftResponse.text()).toBeTruthy()
        const externalDraft = await externalDraftResponse.json() as ConfigurationDraftFixture
        const externalSnapshot = structuredClone(externalDraft.snapshot)
        const externalDescription = `server update ${suffix}`
        externalSnapshot.stateFlow = developmentRequirementFlow(externalDescription)
        const externalSaveResponse = await request.put(
          `${apiBaseUrl}/project-spaces/${space.id}/configuration/types/${requirementTypeId}/draft`,
          {
            headers: {
              ...bearer(owner),
              'X-Colla-Request-Id': `${suffix}-external-state-flow`,
            },
            data: {
              snapshot: externalSnapshot,
              expectedAggregateVersion: externalDraft.aggregateVersion,
            },
          },
        )
        expect(externalSaveResponse.ok(), await externalSaveResponse.text()).toBeTruthy()

        const staleSaveResponse = page.waitForResponse(response =>
          response.url().endsWith(
            `/configuration/types/${requirementTypeId}/draft`,
          )
          && response.request().method() === 'PUT')
        await stateFlowEditor.getByRole('button', {
          name: /保存到草稿$/,
        }).click()
        expect((await staleSaveResponse).status()).toBe(409)
        await expect(stateFlowEditor.getByTestId(
          'work-item-state-flow-conflict',
        )).toBeVisible()
        await expect(stateFlowEditor.getByRole('tab', {
          name: '状态 5',
          exact: true,
        })).toBeVisible()
        await expect(actionTab).toHaveAttribute('aria-selected', 'true')
        await expect(stateFlowEditor.getByLabel('永久 key', {
          exact: true,
        })).toHaveValue('start_progress')
        await expect(stateFlowEditor).toContainText('未保存')
        await expect(stateFlowEditor.getByRole('button', {
          name: /保存到草稿$/,
        })).toBeDisabled()

        await stateFlowEditor.getByRole('button', {
          name: '放弃本地修改',
          exact: true,
        }).click()
        await expect(stateFlowEditor.getByTestId(
          'work-item-state-flow-conflict',
        )).not.toBeVisible()
        await expect(stateFlowEditor.getByRole('tab', {
          name: '状态 7',
          exact: true,
        })).toBeVisible()
        await expect(stateFlowEditor).not.toContainText('未保存')
        await stateFlowEditor.getByRole('tab', {
          name: '状态 7',
          exact: true,
        }).click()
        await stateFlowEditor.locator('.ant-tabs-tabpane-active .ant-collapse-item')
          .first()
          .locator('.ant-collapse-title')
          .click()
        await expect(stateFlowEditor
          .locator('.ant-tabs-tabpane-active .ant-collapse-item')
          .first()
          .getByLabel('说明', { exact: true }))
          .toHaveValue(externalDescription)

        const validateResponse = page.waitForResponse(response =>
          response.url().endsWith(
            `/configuration/types/${requirementTypeId}/draft:validate`,
          )
          && response.request().method() === 'POST')
        await publicationPanel.getByRole('button', {
          name: /校验配置$/,
        }).click()
        expect((await validateResponse).ok()).toBeTruthy()
        await expect(publicationPanel).toContainText('校验通过')

        const publishButton = publicationPanel.getByRole('button', {
          name: /发布版本$/,
        })
        await expect(publishButton).toBeEnabled()
        const publishResponse = page.waitForResponse(response =>
          response.url().endsWith(
            `/configuration/types/${requirementTypeId}/draft:publish`,
          )
          && response.request().method() === 'POST')
        await publishButton.click()
        await page.getByRole('dialog').getByRole('button', {
          name: '发布版本',
          exact: true,
        }).click()
        expect((await publishResponse).ok()).toBeTruthy()
        await expect(publicationPanel).toContainText('当前 v2')
        await expect(publicationPanel.getByRole('listitem').filter({
          hasText: 'v2',
        })).toContainText('当前')

        await page.getByTestId('project-space-primary-navigation')
          .getByRole('button', { name: '概览', exact: true })
          .click()
        const readyRequirementEntry = page.getByTestId(
          'project-space-work-entry-requirement',
        )
        await expect(readyRequirementEntry).toContainText('配置就绪')
        await readyRequirementEntry.getByRole('button', {
          name: '新建需求（requirement）',
          exact: true,
        }).click()

        const createDialog = page.getByRole('dialog')
        await expect(createDialog).toContainText('新建需求')
        const requirementTitle = `研发需求上线闭环 ${suffix}`
        await createDialog.getByLabel('标题', { exact: true }).fill(requirementTitle)
        const [createdResponse] = await Promise.all([
          page.waitForResponse(response =>
            response.url().endsWith(`/project-spaces/${space?.id}/work-items`)
            && response.request().method() === 'POST'),
          createDialog.getByRole('button', {
            name: /创\s*建/,
          }).click(),
        ])
        expect(createdResponse.ok()).toBeTruthy()
        const created = await createdResponse.json() as { id: string }

        await expect(page).toHaveURL(
          new RegExp(`/project-spaces/${space.id}/work-items/${created.id}$`),
        )
        await expect(page.getByTestId('project-work-item-detail')
          .getByLabel('标题', { exact: true }))
          .toHaveValue(requirementTitle)
        await page.getByTestId('project-work-item-detail-secondary-tabs')
          .getByRole('tab', { name: '状态流', exact: true })
          .click()
        const workflowPanel = page.getByTestId('work-item-workflow-panel')
        const stateTag = workflowPanel.locator('.work-item-workflow-state-tag')
        await expect(stateTag).toHaveText('待处理')
        await expect(workflowPanel).toContainText('open')

        const workflowSteps = [
          { actionKey: 'start_progress', stateLabel: '研发中', stateKey: 'in_progress' },
          { actionKey: 'submit_test', stateLabel: '测试中', stateKey: 'testing' },
          { actionKey: 'test_failed', stateLabel: '研发中', stateKey: 'in_progress' },
          { actionKey: 'submit_test', stateLabel: '测试中', stateKey: 'testing' },
          { actionKey: 'test_passed', stateLabel: '待上线', stateKey: 'ready_for_release' },
          { actionKey: 'release', stateLabel: '已上线', stateKey: 'released' },
          { actionKey: 'complete', stateLabel: '已关闭', stateKey: 'done' },
        ]
        for (const step of workflowSteps) {
          const [actionResponse] = await Promise.all([
            page.waitForResponse(response =>
              response.url().endsWith(`/workflow/actions/${step.actionKey}`)
              && response.request().method() === 'POST'),
            workflowPanel
              .getByTestId(`work-item-workflow-action-${step.actionKey}`)
              .click(),
          ])
          expect(actionResponse.ok()).toBeTruthy()
          await expect(stateTag).toHaveText(step.stateLabel)
          await expect(workflowPanel).toContainText(step.stateKey)
        }
        await expect(workflowPanel.getByText('open → in_progress', {
          exact: true,
        })).toBeVisible()
        await expect(workflowPanel.getByText('testing → in_progress', {
          exact: true,
        })).toBeVisible()
        await expect(workflowPanel.getByText('ready_for_release → released', {
          exact: true,
        })).toBeVisible()
        await expect(workflowPanel.getByText('released → done', {
          exact: true,
        })).toBeVisible()

        await page.screenshot({
          path: testInfo.outputPath('s21-m7-reopened-requirement-done.png'),
          fullPage: true,
        })
      } finally {
        if (space) {
          await archiveSpaceFixture(request, owner, space.id)
        }
      }
    },
  )
})

async function createSpaceFixture(
  request: APIRequestContext,
  owner: E2eSession,
  suffix: string,
): Promise<ProjectSpaceFixture> {
  const name = `S21 M7 重开需求闭环 ${suffix}`
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: {
      ...bearer(owner),
      'X-Colla-Request-Id': `${suffix}-create-space`,
    },
    data: {
      spaceKey: `s21-m7-reopened-${suffix.replaceAll('_', '-')}`,
      name,
      description: 'S21 M7 reopened real isolated requirement lifecycle evidence',
      visibility: 'private',
    },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return {
    id: (await response.json() as { id: string }).id,
    name,
  }
}

async function archiveSpaceFixture(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
) {
  const response = await request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`,
    { headers: bearer(owner) },
  )
  expect(response.ok(), await response.text()).toBeTruthy()
}

async function selectedTypeId(page: Page, spaceId: string) {
  await expect.poll(() => new URL(page.url()).pathname).toMatch(
    new RegExp(`/project-spaces/${spaceId}/types/[^/]+$`),
  )
  const typeId = new URL(page.url()).pathname.split('/').at(-1)
  if (!typeId) throw new Error('requirement type id is absent from the selected UI route')
  return typeId
}

async function dismissAutomaticOnboarding(page: Page, spaceId: string) {
  const onboarding = page.getByTestId('project-space-onboarding')
  const openedAutomatically = await onboarding
    .waitFor({ state: 'visible', timeout: 5_000 })
    .then(() => true)
    .catch(() => false)
  if (!openedAutomatically) return

  const dismissButton = page.getByTestId('onboarding-dismiss')
  await expect(dismissButton).toBeVisible()
  const dismissResponse = page.waitForResponse(response =>
    response.url().endsWith(
      `/project-spaces/${spaceId}/onboarding/commands`,
    )
    && response.request().method() === 'POST')
  await dismissButton.click()
  expect((await dismissResponse).ok()).toBeTruthy()
  await expect(onboarding).not.toBeVisible()
  await expect(page.locator('.ant-drawer-mask:visible')).toHaveCount(0)
}

function developmentRequirementFlow(openDescription: string): JsonRecord {
  const states = [
    ['open', '待处理', 'initial', openDescription],
    ['in_progress', '研发中', 'active', '需求正在研发'],
    ['testing', '测试中', 'active', '需求正在测试'],
    ['ready_for_release', '待上线', 'active', '测试通过，等待上线'],
    ['released', '已上线', 'active', '需求已上线，等待关单'],
    ['done', '已关闭', 'terminal', '需求已完成关单'],
    ['canceled', '已取消', 'canceled', '需求已取消'],
  ].map(([stateKey, label, category, description], index) => ({
    stateKey,
    label,
    description,
    color: '',
    category,
    sortOrder: (index + 1) * 100,
  }))
  const actions = [
    ['start_progress', '开始研发', 'forward'],
    ['submit_test', '提交测试', 'forward'],
    ['test_passed', '测试通过', 'forward'],
    ['test_failed', '测试退回', 'return'],
    ['release', '上线', 'forward'],
    ['complete', '关单', 'forward'],
    ['reopen', '重新打开', 'reopen'],
    ['terminate', '取消需求', 'terminate'],
    ['restore', '恢复需求', 'restore'],
  ].map(([actionKey, label, kind], index) => ({
    actionKey,
    label,
    description: '',
    kind,
    authorizedRoles: ['admin', 'member', 'owner'],
    requiredFieldKeys: [],
    fieldPatch: {},
    sideEffectKeys: [],
    sortOrder: (index + 1) * 100,
  }))
  const transitions = [
    ['open_start_progress', 'start_progress', 'open', 'in_progress'],
    ['in_progress_submit_test', 'submit_test', 'in_progress', 'testing'],
    ['testing_test_passed', 'test_passed', 'testing', 'ready_for_release'],
    ['testing_test_failed', 'test_failed', 'testing', 'in_progress'],
    ['ready_for_release_release', 'release', 'ready_for_release', 'released'],
    ['released_complete', 'complete', 'released', 'done'],
    ['done_reopen', 'reopen', 'done', 'open'],
    ['open_terminate', 'terminate', 'open', 'canceled'],
    ['in_progress_terminate', 'terminate', 'in_progress', 'canceled'],
    ['testing_terminate', 'terminate', 'testing', 'canceled'],
    ['ready_for_release_terminate', 'terminate', 'ready_for_release', 'canceled'],
    ['released_terminate', 'terminate', 'released', 'canceled'],
    ['canceled_restore', 'restore', 'canceled', 'open'],
  ].map(([transitionKey, actionKey, fromStateKey, toStateKey], index) => ({
    transitionKey,
    actionKey,
    fromStateKey,
    toStateKey,
    guardKey: null,
    sortOrder: (index + 1) * 100,
  }))
  return { states, actions, transitions, guards: [] }
}
