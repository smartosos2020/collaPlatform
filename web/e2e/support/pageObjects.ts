import { expect, type Page } from '@playwright/test'

export class LoginPage {
  constructor(private readonly page: Page) {}

  async open() {
    await this.page.goto('/login')
    await expect(this.page.getByLabel('账号')).toBeVisible()
  }

  async signIn(username: string, password: string) {
    await this.page.getByLabel('账号').fill(username)
    await this.page.getByLabel('密码').fill(password)
    await this.page.getByRole('button', { name: /登\s*录/ }).click()
  }
}

export class UserWorkspacePage {
  constructor(private readonly page: Page) {}

  async expectVisible() {
    await expect(this.page.getByPlaceholder('搜索事项、知识内容、表格、消息')).toBeVisible()
    await expect(this.page.getByPlaceholder('后台治理搜索：成员、部门、角色、审计动作')).toHaveCount(0)
  }

  async openAccountMenu() {
    const trigger = this.page
      .getByTestId('user-account-menu-trigger')
      .or(this.page.getByRole('button', { name: /Administrator|admin|用户/i }))
      .first()
    await trigger.click()
  }
}

export class AdminConsolePage {
  constructor(private readonly page: Page) {}

  async expectVisible(title: string) {
    await expect(this.page.getByPlaceholder('后台治理搜索：成员、部门、角色、审计动作')).toBeVisible()
    await expect(this.page.getByRole('heading', { name: title })).toBeVisible()
    await expect(this.page.getByPlaceholder('搜索事项、知识内容、表格、消息')).toHaveCount(0)
  }

  async returnToWorkspace() {
    await this.page.getByRole('button', { name: '返回工作台' }).click()
  }
}
