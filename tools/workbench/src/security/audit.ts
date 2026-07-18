import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

interface Assertion { path: string; pattern: RegExp; message: string; absent?: boolean }

export function runSecurityAudit(root: string, writeReport = true): { failures: string[]; results: string[]; report?: string } {
  const assertions: Assertion[] = [
    { path: 'server/pom.xml', pattern: /<spring\.profiles\.active>test<\/spring\.profiles\.active>/, message: 'Backend tests run with the isolated test profile' },
    { path: 'server/src/main/resources/application-test.yml', pattern: /jdbc:tc:postgresql:16:\/\/\/colla_platform_test/, message: 'Test profile uses Testcontainers PostgreSQL' },
    { path: 'server/src/main/resources/application-prod.yml', pattern: /\$\{JWT_ACCESS_SECRET\}/, message: 'Production access-token secret comes from environment' },
    { path: 'server/src/main/resources/application-prod.yml', pattern: /\$\{JWT_REFRESH_SECRET\}/, message: 'Production refresh-token secret comes from environment' },
    { path: 'server/src/main/resources/application-prod.yml', pattern: /change-me-|admin123456|colla_dev_password|colla_minio_password/, message: 'Production profile has no local/default credentials', absent: true },
    { path: 'server/src/main/java/com/colla/platform/config/SecurityConfig.java', pattern: /\.anyRequest\(\)\.authenticated\(\)/, message: 'Security config authenticates non-public routes' },
    { path: 'server/src/main/java/com/colla/platform/modules/audit/application/AuditService.java', pattern: /requireManageUsers/, message: 'Audit log query remains admin-managed' },
  ]
  for (const path of [
    'server/src/main/java/com/colla/platform/modules/identity/application/AuthService.java',
    'server/src/main/java/com/colla/platform/modules/identity/application/MemberService.java',
    'server/src/main/java/com/colla/platform/modules/project/application/ProjectService.java',
    'server/src/main/java/com/colla/platform/modules/knowledge/application/KnowledgeContentService.java',
    'server/src/main/java/com/colla/platform/modules/base/application/BaseService.java',
    'server/src/main/java/com/colla/platform/modules/approval/application/ApprovalService.java',
    'server/src/main/java/com/colla/platform/modules/file/application/FileService.java',
  ]) assertions.push({ path, pattern: /auditService\.log\(/, message: `${path} writes audit events` })

  const failures: string[] = []
  const results: string[] = []
  for (const assertion of assertions) {
    try {
      const matched = assertion.pattern.test(readFileSync(join(root, assertion.path), 'utf8'))
      if (assertion.absent ? matched : !matched) failures.push(assertion.message)
      else results.push(assertion.message)
    } catch {
      failures.push(`${assertion.path} is missing`)
    }
  }
  let report: string | undefined
  if (writeReport) {
    const timestamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z')
    mkdirSync(join(root, '.local-reports'), { recursive: true })
    report = join(root, '.local-reports', `security-audit-gate-${timestamp}.md`)
    writeFileSync(report, ['# Security Audit Gate', '', `- Status: ${failures.length ? 'FAIL' : 'PASS'}`, `- Time: ${new Date().toISOString()}`, '', '## Results', ...results.map((value) => `- PASS: ${value}`), '', '## Failures', ...(failures.length ? failures.map((value) => `- ${value}`) : ['- None']), ''].join('\n'))
  }
  return { failures, results, report }
}
