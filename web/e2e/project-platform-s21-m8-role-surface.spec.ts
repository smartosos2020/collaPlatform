import {
  expect,
  test,
  type APIRequestContext,
  type Browser,
  type BrowserContext,
  type Locator,
  type Page,
} from '@playwright/test'

import {
  apiBaseUrl,
  bearer,
  installSession,
  loginByApi,
  webBaseUrl,
  type E2eSession,
} from './support/api'
import {
  addMember,
  createIdentity,
  createItem,
  getJson,
  publishedProjectType,
  type Identity,
  type Item,
} from './support/crossSpace'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type CurrentUser = {
  id: string
}

type SpaceView = {
  id: string
  name: string
  currentUserRole?: string | null
  availableActions: string[]
}

type TypeSummary = {
  id: string
  typeKey: string
  name: string
  configurationReady: boolean
  availableActions: string[]
}

type PersonalWorkPage = {
  buckets: Array<{
    bucket: 'todo' | 'responsible' | 'participating' | 'watching'
    visibleCount: number
    items: Array<{
      workItemId: string
      title: string
      capabilities: string[]
      availableActions: string[]
      deepLink: string
    }>
  }>
}

type SurfacePreview = {
  schemaVersion: number
  spaceId: string
  targetRole: 'member' | 'guest'
  availableActions: string[]
  defaultPath: string
  readOnly: boolean
  contentIncluded: false
  explanation: string
}

type BrowserSurface = {
  context: BrowserContext
  page: Page
}

const contentRequestFragments = [
  '/members',
  '/work-items',
  '/configuration/',
  '/metrics',
  '/automation',
  '/cross-space',
] as const

test.describe('PROJECT-PLATFORM-S21-M8 role-layered project space', () => {
  test('@smoke closes role surfaces, member work and compatibility in a real isolated stack', async ({
    browser,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    requireIsolatedIdentityFixture()

    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<CurrentUser>(
      request,
      `${apiBaseUrl}/auth/me`,
      enterprise,
    )
    const suffix = `s21m8_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const identities: Identity[] = []
    const surfaces: BrowserSurface[] = []
    let owner: E2eSession | undefined
    let spaceId: string | undefined

    try {
      const ownerIdentity = await provisionIdentity(
        request, enterprise, identities, suffix, 'owner', 'S21 M8 Owner',
      )
      const adminIdentity = await provisionIdentity(
        request, enterprise, identities, suffix, 'admin', 'S21 M8 Space Admin',
      )
      const memberIdentity = await provisionIdentity(
        request, enterprise, identities, suffix, 'member', 'S21 M8 Member',
      )
      const guestIdentity = await provisionIdentity(
        request, enterprise, identities, suffix, 'guest', 'S21 M8 Guest',
      )
      const outsiderIdentity = await provisionIdentity(
        request, enterprise, identities, suffix, 'outsider', 'S21 M8 Outsider',
      )
      const [ownerSession, admin, member, guest, outsider] = await Promise.all([
        loginByApi(request, ownerIdentity.username, ownerIdentity.password),
        loginByApi(request, adminIdentity.username, adminIdentity.password),
        loginByApi(request, memberIdentity.username, memberIdentity.password),
        loginByApi(request, guestIdentity.username, guestIdentity.password),
        loginByApi(request, outsiderIdentity.username, outsiderIdentity.password),
      ])
      owner = ownerSession

      const spaceName = `S21 M8 角色分层 ${suffix}`
      spaceId = await createSpace(request, owner, suffix, spaceName)
      await addMember(request, owner, spaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, spaceId, memberIdentity.id, 'member')
      await addMember(request, owner, spaceId, guestIdentity.id, 'guest')

      const [ownerSpace, adminSpace, memberSpace, guestSpace] = await Promise.all([
        getSpace(request, owner, spaceId),
        getSpace(request, admin, spaceId),
        getSpace(request, member, spaceId),
        getSpace(request, guest, spaceId),
      ])
      for (const managerSpace of [ownerSpace, adminSpace]) {
        expect(managerSpace.availableActions).toEqual(expect.arrayContaining([
          'view_overview',
          'view_work_items',
          'view_project_management',
          'manage_project',
          'view_members',
          'view_settings',
        ]))
      }
      for (const memberSurface of [memberSpace, guestSpace]) {
        expect(memberSurface.availableActions).toEqual(expect.arrayContaining([
          'view_overview',
          'view_work_items',
        ]))
        expect(memberSurface.availableActions).not.toEqual(expect.arrayContaining([
          'view_project_management',
          'view_members',
          'view_settings',
        ]))
      }

      await expectPreviewContract(request, owner, spaceId, 'member', memberSpace)
      await expectPreviewContract(request, admin, spaceId, 'guest', guestSpace)
      for (const deniedSession of [member, guest]) {
        const response = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/surface-preview?targetRole=member`,
          { headers: bearer(deniedSession) },
        )
        expect(response.status()).toBe(403)
      }
      for (const hiddenSession of [outsider, enterprise]) {
        const response = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/surface-preview?targetRole=member`,
          { headers: bearer(hiddenSession) },
        )
        expect(response.status()).toBe(404)
        expect(await response.text()).not.toContain(spaceName)
      }

      const initialTypes = await getJson<TypeSummary[]>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-types`,
        owner,
      )
      const pendingTypes = initialTypes.filter((type) => !type.configurationReady)
      expect(pendingTypes.length).toBeGreaterThan(0)
      expect(pendingTypes.every((type) => type.availableActions.length === 0)).toBeTruthy()

      const published = await publishedProjectType(request, owner, spaceId, suffix)
      const [ownerTypes, memberTypes, guestTypes] = await Promise.all(
        [owner, member, guest].map(session => getJson<TypeSummary[]>(
          request,
          `${apiBaseUrl}/project-spaces/${spaceId}/work-item-types`,
          session,
        )),
      )
      const ownerProjectType = typeById(ownerTypes, published.typeId)
      const memberProjectType = typeById(memberTypes, published.typeId)
      const guestProjectType = typeById(guestTypes, published.typeId)
      const currentPendingTypes = ownerTypes.filter(type => !type.configurationReady)
      expect(ownerProjectType.availableActions).toEqual(['view', 'create'])
      expect(memberProjectType.availableActions).toEqual(['view', 'create'])
      expect(guestProjectType.availableActions).toEqual(['view'])

      const itemTitle = `M8 当前参与事项 ${suffix}`
      const item = await createItem(
        request,
        owner,
        spaceId,
        published.typeId,
        itemTitle,
        `${suffix}-item`,
      )
      await putParticipant(
        request,
        owner,
        spaceId,
        item,
        memberIdentity.id,
        'watcher',
      )
      const memberWork = await getJson<PersonalWorkPage>(
        request,
        `${apiBaseUrl}/personal-work?spaceId=${spaceId}&limit=20`,
        member,
      )
      const watched = bucket(memberWork, 'watching').items.find(
        candidate => candidate.workItemId === item.id,
      )
      expect(watched).toEqual(expect.objectContaining({
        title: itemTitle,
        deepLink: `/project-spaces/${spaceId}/work-items/${item.id}`,
      }))
      expect(watched?.availableActions).toEqual(watched?.capabilities)
      expect(watched?.availableActions).toContain('view')

      const memberBrowser = await openSurface(browser, member)
      surfaces.push(memberBrowser)
      await memberBrowser.page.setViewportSize({ width: 1440, height: 900 })
      await memberBrowser.page.goto(`/project-spaces/${spaceId}`)
      await dismissAutomaticOnboarding(memberBrowser.page, spaceId)
      await expectPrimaryNavigation(memberBrowser.page, ['概览', '工作项'])
      await expect(memberBrowser.page.getByTestId(
        'project-space-task-zone-member-workspace',
      )).toBeVisible()
      await expect(memberBrowser.page.getByTestId(
        'project-space-task-zone-project-management',
      )).toHaveCount(0)
      await expect(memberBrowser.page.getByTestId(
        'project-space-task-zone-space-management',
      )).toHaveCount(0)
      await expect(memberBrowser.page.getByText(itemTitle, { exact: true })).toBeVisible()
      const memberHome = memberBrowser.page.getByTestId('project-space-member-home')
      await expect(memberHome).toBeVisible()
      const workBuckets = memberBrowser.page.locator(
        '[data-testid^="project-space-work-bucket-"]:not([data-testid$="-count"])',
      )
      await expect(workBuckets).toHaveCount(4)
      const todoBucketBox = await memberBrowser.page.getByTestId(
        'project-space-work-bucket-todo',
      ).boundingBox()
      const responsibleBucketBox = await memberBrowser.page.getByTestId(
        'project-space-work-bucket-responsible',
      ).boundingBox()
      const participatingBucketBox = await memberBrowser.page.getByTestId(
        'project-space-work-bucket-participating',
      ).boundingBox()
      const activeTypesBox = await memberBrowser.page.getByTestId(
        'project-space-active-types',
      ).boundingBox()
      expect(todoBucketBox).not.toBeNull()
      expect(responsibleBucketBox).not.toBeNull()
      expect(participatingBucketBox).not.toBeNull()
      expect(activeTypesBox).not.toBeNull()
      if (!todoBucketBox || !responsibleBucketBox || !participatingBucketBox || !activeTypesBox) {
        throw new Error('member home layout boxes are unavailable')
      }
      expect(Math.abs(todoBucketBox.y - responsibleBucketBox.y)).toBeLessThanOrEqual(2)
      expect(responsibleBucketBox.x).toBeGreaterThan(todoBucketBox.x)
      expect(participatingBucketBox.y).toBeGreaterThan(todoBucketBox.y)
      expect(activeTypesBox.y).toBeGreaterThan(participatingBucketBox.y)
      const memberWorkEntries = memberBrowser.page.locator(
        '[data-testid^="project-space-work-entry-"]',
      )
      await expect(
        memberWorkEntries.getByText(ownerProjectType.name, { exact: true }),
      ).toBeVisible()
      for (const pending of currentPendingTypes) {
        await expect(
          memberWorkEntries.getByText(pending.name, { exact: true }),
        ).toHaveCount(0)
      }
      const memberText = await memberBrowser.page.locator('body').innerText()
      expect(memberText).not.toContain(spaceId)
      expect(memberText).not.toContain(published.typeId)

      await memberBrowser.page.getByText(itemTitle, { exact: true }).click()
      await expect(memberBrowser.page).toHaveURL(
        new RegExp(`/project-spaces/${spaceId}/work-items/${item.id}`),
      )
      const actionSummary = memberBrowser.page.getByTestId('work-item-action-summary')
      await expect(actionSummary).toBeVisible()
      await expect(actionSummary).toContainText('当前状态')
      await expect(actionSummary).toContainText('负责人')
      await expect(actionSummary).toContainText('截止时间')
      await expect(actionSummary).toContainText('下一步动作')
      await expect(actionSummary).toContainText(ownerIdentity.displayName)
      await expect(actionSummary).not.toContainText(item.id)
      await memberBrowser.page.getByTestId('project-work-item-detail-secondary-tabs')
        .getByRole('tab', { name: '状态流程', exact: true })
        .click()
      const workflowTabPanel = memberBrowser.page.getByRole('tabpanel', {
        name: '状态流程',
        exact: true,
      })
      await expect(workflowTabPanel).toBeVisible()
      await expect(workflowTabPanel).not.toContainText(
        /策略|policyVersion|currentStateKey/,
      )

      await memberBrowser.page.goto(`/project-spaces/${spaceId}`)
      const secondTab = await memberBrowser.context.newPage()
      await secondTab.goto(`/project-spaces/${spaceId}`)
      await expect(secondTab.getByText(itemTitle, { exact: true })).toBeVisible()
      await secondTab.close()
      for (const width of [1366, 820]) {
        await memberBrowser.page.setViewportSize({ width, height: 900 })
        await memberBrowser.page.goto(`/project-spaces/${spaceId}`)
        await expect(memberBrowser.page.getByTestId(
          'project-space-primary-navigation',
        )).toBeVisible()
        await expect(memberBrowser.page.getByText(itemTitle, { exact: true })).toBeVisible()
        expect(await documentOverflow(memberBrowser.page)).toBeLessThanOrEqual(1)
      }

      await memberBrowser.page.setViewportSize({ width: 820, height: 900 })
      await memberBrowser.page.goto(`/project-spaces/${spaceId}`)
      const createEntry = memberBrowser.page.getByRole('button', {
        name: `新建${memberProjectType.name}`,
        exact: true,
      })
      await expect(createEntry).toBeVisible()
      await createEntry.click()
      const offlineCreate = memberBrowser.page.getByRole('dialog', {
        name: `新建${memberProjectType.name}`,
      })
      const offlineTitle = offlineCreate.getByLabel('标题', { exact: true })
      const offlineSubmit = offlineCreate.getByRole('button', { name: /^创\s*建$/ })
      await expect(offlineTitle).toBeVisible()
      await offlineTitle.fill(`离线保留 ${suffix}`)
      await expect(offlineSubmit).toBeEnabled()
      await memberBrowser.context.setOffline(true)
      await expect(offlineSubmit).toBeDisabled()
      await expect(offlineTitle).toHaveValue(`离线保留 ${suffix}`)
      await expect(offlineCreate).not.toContainText(/Failed to fetch|Network Error/i)
      await memberBrowser.context.setOffline(false)
      await expect(offlineSubmit).toBeEnabled()
      await offlineCreate.getByRole('button', { name: /^取\s*消$/ }).click()

      const ownerBrowser = await openSurface(browser, owner)
      surfaces.push(ownerBrowser)
      await ownerBrowser.page.setViewportSize({ width: 1440, height: 900 })
      await ownerBrowser.page.goto(
        `/project-spaces/${spaceId}?source=m8-navigation-regression`,
      )
      await dismissAutomaticOnboarding(ownerBrowser.page, spaceId)
      await expectPrimaryNavigation(
        ownerBrowser.page,
        ['概览', '工作项', '项目管理', '成员', '设置'],
      )
      await expect(ownerBrowser.page.getByTestId(
        'project-space-task-zone-member-workspace',
      )).toBeVisible()
      await expect(ownerBrowser.page.getByTestId(
        'project-space-task-zone-project-management',
      )).toBeVisible()
      await expect(ownerBrowser.page.getByTestId(
        'project-space-task-zone-space-management',
      )).toBeVisible()

      await ownerBrowser.page.getByTestId('project-space-overview-secondary-tabs')
        .getByRole('tab', { name: '动态与边界', exact: true })
        .click()
      await expect(ownerBrowser.page).toHaveURL(/panel=activity/)
      await expect(ownerBrowser.page.getByTestId('project-space-activity-boundary'))
        .toContainText('空间动态')
      await expect(ownerBrowser.page.getByTestId('project-space-activity-boundary'))
        .toContainText('协作边界')
      const activityCardBox = await ownerBrowser.page.getByTestId(
        'project-space-activity-card',
      ).boundingBox()
      const boundaryCardBox = await ownerBrowser.page.getByTestId(
        'project-space-boundary-card',
      ).boundingBox()
      expect(activityCardBox).not.toBeNull()
      expect(boundaryCardBox).not.toBeNull()
      if (!activityCardBox || !boundaryCardBox) {
        throw new Error('activity and boundary layout boxes are unavailable')
      }
      expect(activityCardBox.width).toBeGreaterThan(boundaryCardBox.width)
      expect(Math.abs(activityCardBox.y - boundaryCardBox.y)).toBeLessThanOrEqual(2)
      expect(Math.abs(activityCardBox.height - boundaryCardBox.height)).toBeLessThanOrEqual(2)
      const ownerNavigation = ownerBrowser.page.getByRole('navigation', {
        name: '空间导航',
      })
      await ownerNavigation.getByRole('button', {
        name: '工作项',
        exact: true,
      }).click()
      await expect(ownerBrowser.page.getByTestId(
        'project-work-items-secondary-tabs',
      )).toBeVisible()
      await expect(ownerNavigation.getByRole('button', {
        name: '工作项',
        exact: true,
      })).toHaveAttribute('aria-current', 'page')
      await ownerBrowser.page.waitForTimeout(300)
      const workItemsLocation = new URL(ownerBrowser.page.url())
      expect(workItemsLocation.pathname).toBe(
        `/project-spaces/${spaceId}/work-items`,
      )
      expect(workItemsLocation.searchParams.get('source')).toBe(
        'm8-navigation-regression',
      )
      expect(workItemsLocation.searchParams.has('panel')).toBe(false)

      await ownerBrowser.page.getByRole('navigation', { name: '空间导航' })
        .getByRole('button', { name: '设置', exact: true })
        .click()
      await expect(ownerBrowser.page.getByRole('tab', {
        name: '管理首页',
        exact: true,
      })).toHaveAttribute('aria-selected', 'true')
      await expect(ownerBrowser.page.getByRole('tab', { name: '基本信息', exact: true })).toHaveCount(0)
      await expect(ownerBrowser.page.getByRole('tab', { name: '启用、停用与归档', exact: true })).toHaveCount(0)
      await expect(ownerBrowser.page.getByTestId('project-space-management-summary')).toHaveCount(0)
      const basicInformation = ownerBrowser.page.getByTestId('project-space-basic-information')
      await expect(basicInformation.getByLabel('空间名称', { exact: true })).toBeVisible()
      await expect(basicInformation.getByLabel('可见性', { exact: true })).toBeVisible()
      await expect(basicInformation.getByLabel('空间说明', { exact: true })).toBeVisible()
      await expect(basicInformation.getByRole('button', { name: '保存设置', exact: true })).toBeVisible()
      await expect(basicInformation.getByRole('button').filter({ hasText: /^停用$/ }))
        .toHaveCount(ownerSpace.availableActions.includes('disable') ? 1 : 0)
      await expect(basicInformation.getByRole('button').filter({ hasText: /^归档$/ }))
        .toHaveCount(ownerSpace.availableActions.includes('archive') ? 1 : 0)
      await expect(ownerBrowser.page.getByText('B. 配置健康', { exact: true })).toBeVisible()
      await expect(ownerBrowser.page.getByText('C. 配置待办', { exact: true })).toBeVisible()
      await expect(ownerBrowser.page.getByText('D. 最近入口', { exact: true })).toBeVisible()
      await expect(ownerBrowser.page.getByText('危险操作', { exact: true })).toHaveCount(0)
      await expect(ownerBrowser.page.getByText(
        `发布“${currentPendingTypes[0].name}”的配置`,
        { exact: true },
      )).toBeVisible()

      const openSettingsGroup = async (group: '工作模型') => {
        await ownerBrowser.page.getByRole('tab', { name: group, exact: true }).click()
        await expect(ownerBrowser.page.getByRole('tabpanel', {
          name: group,
          exact: true,
        })).toBeVisible()
      }

      await openSettingsGroup('工作模型')
      await expect(ownerBrowser.page.locator(
        '[data-testid="project-space-settings-secondary-tabs"] > .ant-tabs > .ant-tabs-nav',
      ).getByRole('tab', { name: '流程与权限', exact: true })).toHaveCount(0)
      const workModelPanel = ownerBrowser.page.getByRole('tabpanel', {
        name: '工作模型',
        exact: true,
      })
      await expect(workModelPanel.getByTestId('work-item-types-panel')).toBeVisible()
      await expect(ownerBrowser.page.getByTestId('project-space-types-secondary-tabs')).toHaveCount(0)
      await expect(workModelPanel.getByText(
        '管理任务模板、字段、表单与页面，以及发布配置。',
        { exact: true },
      )).toHaveCount(0)
      const typeListActions = workModelPanel.getByTestId('work-item-type-list-actions')
      await expect(typeListActions.getByRole('button', { name: '复制', exact: true })).toBeDisabled()
      await expect(typeListActions.getByRole('button', { name: '停用', exact: true })).toBeDisabled()
      const publishedTypeOption = workModelPanel
        .getByRole('listbox', { name: '选择工作项类型' })
        .getByRole('option')
        .filter({ hasText: ownerProjectType.typeKey })
      await expect(publishedTypeOption).toHaveCount(1)
      const typeListCard = workModelPanel.locator('.work-item-type-list-card')
      const typeListBox = await typeListCard.boundingBox()
      expect(typeListBox?.width).toBeGreaterThanOrEqual(208)
      expect(typeListBox?.width).toBeLessThanOrEqual(240)
      const typeStatusFilter = typeListCard.getByRole('radiogroup', { name: '工作项类型状态筛选' })
      await expect(typeStatusFilter).toBeVisible()
      expect(await typeStatusFilter.locator('.ant-segmented-item-label').allTextContents()).toEqual([
        '全',
        '使用中',
        '已停',
        '已退',
      ])
      await publishedTypeOption.click()
      await expect.poll(() => new URL(ownerBrowser.page.url()).searchParams.get('typeId'))
        .toBe(published.typeId)
      await expect(publishedTypeOption).toHaveAttribute('aria-selected', 'true')
      const workModelLocation = new URL(ownerBrowser.page.url())
      expect(workModelLocation.pathname).toBe(`/project-spaces/${spaceId}/settings`)
      expect(workModelLocation.searchParams.get('panel')).toBe('work-model')
      expect(workModelLocation.searchParams.get('typeId')).toBe(published.typeId)
      await expect(typeListActions.getByRole('button', { name: '复制', exact: true })).toBeEnabled()
      await expect(typeListActions.getByRole('button', { name: '停用', exact: true })).toBeEnabled()
      expect(await typeListActions.getByRole('button').allTextContents()).toEqual([
        '新建类型',
        '复制',
        '停用',
      ])
      await expect(workModelPanel.getByRole('button').filter({
        hasText: /^任务模板$/,
      })).toHaveCount(0)
      for (const removedButton of [
        '字段',
        '表单与页面',
        '发布配置',
        '进入任务模板的流程与权限配置',
      ]) {
        await expect(workModelPanel.getByRole('button').filter({
          hasText: new RegExp(`^${removedButton}$`),
        })).toHaveCount(0)
      }
      await expect(ownerBrowser.page.getByRole('dialog', {
        name: /^选择任务模板/,
      })).toHaveCount(0)
      const typeDetailCard = workModelPanel.locator('.work-item-type-detail-card')
      const typeInformationTab = typeDetailCard.getByRole('tab', {
        name: '类型信息',
        exact: true,
      })
      const fieldConfigurationTab = typeDetailCard.getByRole('tab', {
        name: '配置字段',
        exact: true,
      })
      const pageLayoutTab = typeDetailCard.getByRole('tab', {
        name: '页面布局',
        exact: true,
      })
      const flowAccessTab = typeDetailCard.getByRole('tab', {
        name: '流程与权限',
        exact: true,
      })
      await expect(typeInformationTab).toHaveAttribute('aria-selected', 'true')
      await expect(fieldConfigurationTab).toBeVisible()
      await expect(pageLayoutTab).toBeVisible()
      await expect(flowAccessTab).toBeVisible()
      const settingsTabsBox = await ownerBrowser.page
        .getByTestId('project-space-settings-secondary-tabs')
        .boundingBox()
      const managementHomeTabBox = await ownerBrowser.page
        .getByRole('tab', { name: '管理首页', exact: true })
        .boundingBox()
      const typeDetailCardBox = await typeDetailCard.boundingBox()
      const typeInformationTabBox = await typeInformationTab.boundingBox()
      const fieldConfigurationTabBox = await fieldConfigurationTab.boundingBox()
      const pageLayoutTabBox = await pageLayoutTab.boundingBox()
      const flowAccessTabBox = await flowAccessTab.boundingBox()
      expect(settingsTabsBox).not.toBeNull()
      expect(managementHomeTabBox).not.toBeNull()
      expect(typeDetailCardBox).not.toBeNull()
      expect(typeInformationTabBox).not.toBeNull()
      expect(fieldConfigurationTabBox).not.toBeNull()
      expect(pageLayoutTabBox).not.toBeNull()
      expect(flowAccessTabBox).not.toBeNull()
      const managementTabTopOffset = (managementHomeTabBox?.y ?? 0) - (settingsTabsBox?.y ?? 0)
      const detailTabTopOffset = (typeInformationTabBox?.y ?? 0) - (typeDetailCardBox?.y ?? 0)
      expect(Math.abs(detailTabTopOffset - managementTabTopOffset)).toBeLessThanOrEqual(1)
      expect(fieldConfigurationTabBox?.y).toBe(typeInformationTabBox?.y)
      expect(pageLayoutTabBox?.y).toBe(typeInformationTabBox?.y)
      expect(flowAccessTabBox?.y).toBe(typeInformationTabBox?.y)
      expect(flowAccessTabBox?.x).toBeGreaterThan(pageLayoutTabBox?.x ?? 0)
      const readTabTypography = (tab: typeof typeInformationTab) => tab.evaluate((element) => {
        const style = getComputedStyle(element)
        return {
          fontFamily: style.fontFamily,
          fontSize: style.fontSize,
          fontStyle: style.fontStyle,
          fontWeight: style.fontWeight,
          letterSpacing: style.letterSpacing,
          lineHeight: style.lineHeight,
          textTransform: style.textTransform,
        }
      })
      const managementTabTypography = await readTabTypography(
        ownerBrowser.page.getByRole('tab', { name: '管理首页', exact: true }),
      )
      expect(await readTabTypography(typeInformationTab)).toEqual(managementTabTypography)
      expect(await readTabTypography(fieldConfigurationTab)).toEqual(managementTabTypography)
      expect(await readTabTypography(pageLayoutTab)).toEqual(managementTabTypography)
      expect(await readTabTypography(flowAccessTab)).toEqual(managementTabTypography)
      await expect(typeDetailCard.getByRole('button', { name: '复制', exact: true })).toHaveCount(0)
      await expect(typeDetailCard.getByRole('button', { name: '停用', exact: true })).toHaveCount(0)
      const typeInformationHeader = typeDetailCard.getByTestId('work-item-model-section-header')
      await expect(typeInformationHeader.locator('.status-badge')).toHaveCount(0)
      await expect(typeInformationHeader.locator('code')).toHaveCount(0)

      const readSectionHeaderContract = (header: Locator) => header.evaluate((element) => {
          const style = getComputedStyle(element)
          const title = element.querySelector('h3, h4')
          const titleStyle = title ? getComputedStyle(title) : null
          return {
            backgroundColor: style.backgroundColor,
            borderTop: style.borderTop,
            borderRadius: style.borderRadius,
            boxShadow: style.boxShadow,
            minHeight: style.minHeight,
            padding: style.padding,
            titleFontSize: titleStyle?.fontSize,
            titleLineHeight: titleStyle?.lineHeight,
            width: Math.round(element.getBoundingClientRect().width * 100) / 100,
          }
        })
      const typeInformationHeaderContract = await readSectionHeaderContract(typeInformationHeader)

      const expectWorkModelLocation = () => expect.poll(() => {
        const current = new URL(ownerBrowser.page.url())
        return {
          pathname: current.pathname,
          panel: current.searchParams.get('panel'),
          typeId: current.searchParams.get('typeId'),
        }
      }).toEqual({
        pathname: `/project-spaces/${spaceId}/settings`,
        panel: 'work-model',
        typeId: published.typeId,
      })

      await fieldConfigurationTab.click()
      const fieldPanel = typeDetailCard.getByTestId('work-item-fields-panel')
      await expect(fieldPanel).toBeVisible()
      await expect(typeDetailCard.getByRole('button', { name: '返回类型', exact: true })).toHaveCount(0)
      const fieldHeader = fieldPanel.locator('.work-item-field-header')
      const fieldHeaderCopy = fieldHeader.locator('.work-item-field-header-copy')
      await expect(fieldHeaderCopy.getByRole('heading', { name: '字段配置', exact: true })).toBeVisible()
      await expect(fieldHeaderCopy.locator('.work-item-field-type-context')).toHaveText(
        `${ownerProjectType.name} · ${ownerProjectType.typeKey}`,
      )
      const fieldFilters = fieldHeader.getByRole('group', { name: '字段目录筛选' })
      const fieldListCard = fieldPanel.locator('.work-item-field-list-card')
      await expect(fieldListCard.getByRole('searchbox', { name: '搜索字段名称或字段键' })).toBeVisible()
      await expect(fieldFilters.getByRole('button').filter({ hasText: /^新建字段$/ }))
        .toBeVisible()
      const fieldStatusFilter = fieldListCard.getByRole('radiogroup', { name: '字段状态筛选' })
      await expect(fieldStatusFilter).toBeVisible()
      await expect(fieldFilters.getByRole('combobox', { name: '字段类型筛选' })).toBeVisible()
      await expect(fieldFilters.getByRole('combobox', { name: '字段排序方式' })).toBeVisible()
      expect(await fieldStatusFilter.locator('.ant-segmented-item-label').allTextContents()).toEqual([
        '全',
        '使用中',
        '已停',
        '已退',
      ])
      expect(await fieldHeader.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBeTruthy()
      expect(await readSectionHeaderContract(
        fieldPanel.getByTestId('work-item-model-section-header'),
      )).toEqual(typeInformationHeaderContract)
      await expectWorkModelLocation()

      await pageLayoutTab.click()
      const embeddedLayout = typeDetailCard.getByTestId('work-item-layouts-panel')
      await expect(embeddedLayout).toBeVisible()
      await expect(embeddedLayout.getByRole('button', { name: '返回类型', exact: true })).toHaveCount(0)
      expect(await readSectionHeaderContract(
        embeddedLayout.getByTestId('work-item-model-section-header'),
      )).toEqual(typeInformationHeaderContract)
      await expect(ownerBrowser.page.getByRole('tab', {
        name: '发布配置',
        exact: true,
      })).toHaveCount(0)
      await expect(ownerBrowser.page.getByRole('tab', {
        name: '工作模型',
        exact: true,
      })).toHaveAttribute('aria-selected', 'true')
      await expectWorkModelLocation()
      await typeInformationTab.click()
      await expect(typeInformationTab).toHaveAttribute('aria-selected', 'true')
      await expect(publishedTypeOption).toHaveAttribute('aria-selected', 'true')

      await flowAccessTab.click()
      const flowAccessPanel = typeDetailCard.getByRole('tabpanel', {
        name: '流程与权限',
        exact: true,
      })
      await expect(ownerBrowser.page.getByTestId('project-space-flow-access-settings'))
        .toHaveCount(0)
      const flowAccessLocation = new URL(ownerBrowser.page.url())
      expect(flowAccessLocation.pathname).toBe(`/project-spaces/${spaceId}/settings`)
      expect(flowAccessLocation.searchParams.get('panel')).toBe('work-model')
      expect(flowAccessLocation.searchParams.get('workModelTab')).toBe('flow-access')
      expect(flowAccessLocation.searchParams.get('typeId')).toBe(published.typeId)
      await expect(ownerBrowser.page.getByRole('combobox', {
        name: '当前任务模板',
        exact: true,
      })).toHaveCount(0)
      await expect(publishedTypeOption).toHaveAttribute('aria-selected', 'true')
      await expect(flowAccessPanel.getByRole('region', {
        name: '配置草稿状态',
        exact: true,
      })).toBeVisible()
      const flowAccessRegion = flowAccessPanel.getByRole('region', {
        name: '流程与权限',
        exact: true,
      })
      await expect(flowAccessRegion).toBeVisible()
      await expect(flowAccessRegion.getByText('数据权限策略', { exact: true })).toBeVisible()
      const flowEditor = flowAccessRegion.locator(
        '[data-testid="work-item-state-flow-editor"], '
        + '[data-testid="work-item-node-flow-designer"]',
      )
      await expect(flowEditor).toHaveCount(1)
      await expect(flowEditor).toBeVisible()
      await expect(ownerBrowser.page.getByRole('tab', {
        name: '发布配置',
        exact: true,
      })).toHaveCount(0)
      await expect(flowAccessPanel.getByRole('button').filter({
        hasText: /^进入任务模板的流程与权限配置$/,
      })).toHaveCount(0)
      await expect(ownerBrowser.page.getByRole('dialog', {
        name: /^选择任务模板/,
      })).toHaveCount(0)

      await ownerBrowser.page.reload()
      await expect(ownerBrowser.page.getByRole('tab', {
        name: '工作模型',
        exact: true,
      })).toHaveAttribute('aria-selected', 'true')
      await expect(typeDetailCard.getByRole('tab', {
        name: '流程与权限',
        exact: true,
      })).toHaveAttribute('aria-selected', 'true')
      await expect(ownerBrowser.page.getByRole('region', {
        name: '配置草稿状态',
        exact: true,
      })).toBeVisible()
      await expect(publishedTypeOption).toHaveAttribute('aria-selected', 'true')
      const reloadedFlowAccessLocation = new URL(ownerBrowser.page.url())
      expect(reloadedFlowAccessLocation.pathname).toBe(`/project-spaces/${spaceId}/settings`)
      expect(reloadedFlowAccessLocation.searchParams.get('panel')).toBe('work-model')
      expect(reloadedFlowAccessLocation.searchParams.get('workModelTab')).toBe('flow-access')
      expect(reloadedFlowAccessLocation.searchParams.get('typeId')).toBe(published.typeId)

      await ownerBrowser.page.goto(
        `/project-spaces/${spaceId}/settings?panel=flow-access&source=m8-inline-direct`,
      )
      await expect.poll(() => {
        const current = new URL(ownerBrowser.page.url())
        return {
          panel: current.searchParams.get('panel'),
          workModelTab: current.searchParams.get('workModelTab'),
          typeId: current.searchParams.get('typeId'),
        }
      }).toEqual({
        panel: 'work-model',
        workModelTab: 'flow-access',
        typeId: null,
      })
      await expect(ownerBrowser.page.getByRole('combobox', {
        name: '当前任务模板',
        exact: true,
      })).toHaveCount(0)
      await expect(ownerBrowser.page.getByRole('region', {
        name: '配置草稿状态',
        exact: true,
      })).toHaveCount(0)
      const directTypeOption = ownerBrowser.page
        .getByRole('listbox', { name: '选择工作项类型' })
        .getByRole('option')
        .filter({ hasText: ownerProjectType.typeKey })
      await expect(directTypeOption).toBeVisible()
      await directTypeOption.click()
      await expect.poll(() => new URL(ownerBrowser.page.url()).searchParams.get('typeId'))
        .toBe(published.typeId)
      await expect.poll(() => new URL(ownerBrowser.page.url()).searchParams.get('workModelTab'))
        .toBe('flow-access')
      await expect(ownerBrowser.page.getByRole('region', {
        name: '配置草稿状态',
        exact: true,
      })).toBeVisible()

      await ownerBrowser.page.getByRole('navigation', { name: '空间导航' })
        .getByRole('button', { name: '设置', exact: true })
        .click()
      await expect(ownerBrowser.page.getByRole('tab', {
        name: '管理首页',
        exact: true,
      })).toHaveAttribute('aria-selected', 'true')

      const previewContentRequests: string[] = []
      const requestListener = (browserRequest: { url(): string }) => {
        const url = browserRequest.url()
        if (contentRequestFragments.some(fragment => url.includes(fragment))) {
          previewContentRequests.push(url)
        }
      }
      ownerBrowser.page.on('request', requestListener)
      const contextBeforePreview = ownerBrowser.page.url()
      await ownerBrowser.page.getByTestId('project-space-member-preview-open').click()
      const preview = ownerBrowser.page.getByRole('dialog', {
        name: '成员视图预览',
      })
      await expect(preview).toBeVisible()
      await expect(preview).toContainText('这是入口展示预览，不会改变你的权限')
      await expect(preview.getByText('成员工作区', { exact: true })).toBeVisible()
      await expect(preview.getByText('项目管理', { exact: true })).toHaveCount(0)
      await expect(preview.getByText('空间管理', { exact: true })).toHaveCount(0)
      await preview.getByText('访客', { exact: true }).click()
      await expect(preview.getByLabel('访客可见入口')).toBeVisible()
      await ownerBrowser.page.waitForTimeout(300)
      expect(previewContentRequests).toEqual([])
      await ownerBrowser.page.keyboard.press('Escape')
      await expect(preview).not.toBeVisible()
      expect(ownerBrowser.page.url()).toBe(contextBeforePreview)
      await expect(
        ownerBrowser.page.getByTestId('project-space-member-preview-open'),
      ).toBeFocused()
      ownerBrowser.page.off('request', requestListener)

      await ownerBrowser.page.goto(`/project-spaces/${spaceId}`)
      await ownerBrowser.page.getByLabel('新建项目空间').click()
      const createSpaceDialog = ownerBrowser.page.getByRole('dialog', {
        name: '新建项目空间',
      })
      const startingImpact = (label: string) => createSpaceDialog.getByText(
        label,
        { exact: true },
      ).locator('..')
      await expect(startingImpact('起步路径')).toContainText('基础空间')
      await expect(createSpaceDialog).toContainText('保留平台基础任务模板')

      await createSpaceDialog.getByText('克隆配置', { exact: true }).click()
      const referenceSelect = createSpaceDialog.getByRole('combobox', {
        name: /参考空间/,
      })
      await referenceSelect.click()
      await ownerBrowser.page.getByTitle(spaceName, { exact: true }).click()
      await expect(startingImpact('参考来源')).toContainText(spaceName)
      await expect(createSpaceDialog).toContainText('成员、工作项、附件和权限')

      await createSpaceDialog.getByText('场景模板', { exact: true }).click()
      const scenarioSelect = createSpaceDialog.getByRole('combobox', {
        name: /场景模板/,
      })
      await scenarioSelect.click()
      for (const scenario of ['研发协作', '市场项目', 'HR 事务', '交付管理']) {
        await expect(ownerBrowser.page.getByTitle(scenario, { exact: true })).toBeVisible()
      }
      await ownerBrowser.page.getByTitle('研发协作', { exact: true }).click()
      await expect(startingImpact('起步路径')).toContainText('研发协作')
      await expect(createSpaceDialog).toContainText('只保存引导选择')
      await expect(createSpaceDialog).toContainText('不会自动安装或发布配置')
      await createSpaceDialog.getByRole('button', { name: /^取\s*消$/ }).click()

      const compatibilityLocation = `/project-spaces/${spaceId}/work-items`
        + `?panel=automation-rules&source=bookmark&typeId=${published.typeId}`
        + '&create=1&savedViewId=view-1#focus'
      await ownerBrowser.page.goto(compatibilityLocation)
      await expect.poll(() => new URL(ownerBrowser.page.url()).pathname).toBe(
        `/project-spaces/${spaceId}/settings`,
      )
      const canonicalLocation = new URL(ownerBrowser.page.url())
      expect(Object.fromEntries(canonicalLocation.searchParams)).toEqual({
        source: 'bookmark',
        panel: 'automation-collaboration',
        automationPanel: 'automation-rules',
        typeId: published.typeId,
        create: '1',
        savedViewId: 'view-1',
      })
      expect(canonicalLocation.hash).toBe('#focus')
      await expect(ownerBrowser.page.getByRole('tab', {
        name: '自动化与协同',
        exact: true,
      })).toHaveAttribute('aria-selected', 'true')
      await expect(ownerBrowser.page.getByTestId('project-space-settings-automation')
        .getByRole('tab', {
          name: '自动化规则',
          exact: true,
        })).toHaveAttribute('aria-selected', 'true')
      await expect(ownerBrowser.page.getByTestId('automation-rules-panel')).toBeVisible()

      const guestBrowser = await openSurface(browser, guest)
      surfaces.push(guestBrowser)
      await guestBrowser.page.goto(`/project-spaces/${spaceId}`)
      await dismissAutomaticOnboarding(guestBrowser.page, spaceId)
      await expectPrimaryNavigation(guestBrowser.page, ['概览', '工作项'])
      await expect(guestBrowser.page.getByRole('button', {
        name: `新建${guestProjectType.name}`,
        exact: true,
      })).toHaveCount(0)
      const guestViewEntry = guestBrowser.page.getByRole('button', {
        name: `查看${guestProjectType.name}`,
        exact: true,
      })
      await expect(guestViewEntry).toBeVisible()
      await guestViewEntry.click()
      await expect.poll(() => new URL(guestBrowser.page.url()).pathname).toBe(
        `/project-spaces/${spaceId}/work-items`,
      )
      const guestWorkLocation = new URL(guestBrowser.page.url())
      expect(guestWorkLocation.searchParams.get('typeId')).toBe(published.typeId)
      expect(guestWorkLocation.searchParams.has('create')).toBeFalsy()
      await expect(guestBrowser.page.getByTestId('project-work-items')).toBeVisible()
      await expect(guestBrowser.page.getByRole('dialog', {
        name: `新建${guestProjectType.name}`,
      })).toHaveCount(0)
      await expect(guestBrowser.page.getByTestId('project-space-member-preview-open')).toHaveCount(0)

      await ownerBrowser.page.screenshot({
        path: testInfo.outputPath('s21-m8-space-management.png'),
        fullPage: true,
      })
      await memberBrowser.page.screenshot({
        path: testInfo.outputPath('s21-m8-member-workspace-820.png'),
        fullPage: true,
      })
    } finally {
      for (const surface of surfaces.reverse()) {
        await surface.context.setOffline(false).catch(() => undefined)
        await surface.context.close().catch(() => undefined)
      }
      if (spaceId && owner) {
        await request.post(
          `${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`,
          { headers: bearer(owner) },
        ).catch(() => undefined)
      }
      for (const identity of identities.reverse()) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterprise),
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})

async function provisionIdentity(
  request: APIRequestContext,
  enterprise: E2eSession,
  identities: Identity[],
  suffix: string,
  role: string,
  displayName: string,
) {
  const identity = await createIdentity(
    request,
    enterprise,
    `${suffix}_${role}`,
    displayName,
  )
  identities.push(identity)
  return identity
}

async function createSpace(
  request: APIRequestContext,
  owner: E2eSession,
  suffix: string,
  name: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: {
      ...bearer(owner),
      'X-Colla-Request-Id': `${suffix}-create-space`,
    },
    data: {
      spaceKey: `s21-m8-${suffix.replaceAll('_', '-')}`,
      name,
      description: 'S21 M8 real isolated role-layered UX evidence',
      visibility: 'private',
    },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

function getSpace(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
) {
  return getJson<SpaceView>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}`,
    session,
  )
}

async function expectPreviewContract(
  request: APIRequestContext,
  manager: E2eSession,
  spaceId: string,
  targetRole: SurfacePreview['targetRole'],
  actual: SpaceView,
) {
  const response = await request.get(
    `${apiBaseUrl}/project-spaces/${spaceId}/surface-preview?targetRole=${targetRole}`,
    { headers: bearer(manager) },
  )
  expect(response.ok(), await response.text()).toBeTruthy()
  expect(response.headers()['cache-control']).toBe('private, no-store')
  const preview = await response.json() as SurfacePreview
  expect(Object.keys(preview).sort()).toEqual([
    'availableActions',
    'contentIncluded',
    'defaultPath',
    'explanation',
    'readOnly',
    'schemaVersion',
    'spaceId',
    'targetRole',
  ])
  expect(preview.availableActions).toEqual(actual.availableActions)
  expect(preview.contentIncluded).toBe(false)
  expect(preview.defaultPath).toBe(`/project-spaces/${spaceId}`)
}

function typeById(types: TypeSummary[], typeId: string) {
  const type = types.find(candidate => candidate.id === typeId)
  if (!type) throw new Error(`published type ${typeId} is absent`)
  return type
}

function bucket(page: PersonalWorkPage, key: PersonalWorkPage['buckets'][number]['bucket']) {
  const result = page.buckets.find(candidate => candidate.bucket === key)
  if (!result) throw new Error(`personal work bucket ${key} is absent`)
  return result
}

async function putParticipant(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  item: Item,
  userId: string,
  role: string,
) {
  const response = await request.put(
    `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${item.id}/participants/${userId}`,
    {
      headers: {
        ...bearer(owner),
        'X-Colla-Request-Id': `s21-m8-participant-${userId}`,
      },
      data: { role, expectedVersion: item.version },
    },
  )
  expect(response.ok(), await response.text()).toBeTruthy()
}

async function openSurface(
  browser: Browser,
  session: E2eSession,
): Promise<BrowserSurface> {
  const context = await browser.newContext({ baseURL: webBaseUrl })
  const page = await context.newPage()
  page.setDefaultTimeout(25_000)
  await installSession(page, session)
  return { context, page }
}

async function expectPrimaryNavigation(page: Page, expected: string[]) {
  const navigation = page.getByRole('navigation', { name: '空间导航' })
  await expect(navigation).toBeVisible()
  const buttons = navigation.locator('button[aria-label]')
  await expect(buttons).toHaveCount(expected.length)
  expect(await buttons.evaluateAll(elements => elements.map(
    element => element.getAttribute('aria-label'),
  ))).toEqual(expected)
}

async function dismissAutomaticOnboarding(page: Page, spaceId: string) {
  const onboarding = page.getByTestId('project-space-onboarding')
  const opened = await onboarding
    .waitFor({ state: 'visible', timeout: 5_000 })
    .then(() => true)
    .catch(() => false)
  if (!opened) return
  const dismissResponse = page.waitForResponse(response => (
    response.url().endsWith(`/project-spaces/${spaceId}/onboarding/commands`)
    && response.request().method() === 'POST'
  ))
  await page.getByTestId('onboarding-dismiss').click()
  expect((await dismissResponse).ok()).toBeTruthy()
  await expect(onboarding).not.toBeVisible()
}

function documentOverflow(page: Page) {
  return page.evaluate(() => (
    document.documentElement.scrollWidth - document.documentElement.clientWidth
  ))
}
