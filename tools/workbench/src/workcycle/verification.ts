export type VerificationLevel =
  | 'static'
  | 'unit'
  | 'integration'
  | 'system-real-isolated'
  | 'e2e-real'
  | 'e2e-real-isolated'

export interface VerificationContract {
  closureClass: 'non-core' | 'core-user' | 'core-system' | 'legacy-inferred'
  level: VerificationLevel
  browserKind: 'real' | 'mock' | 'not-required'
  environment: 'isolated' | 'shared-readonly' | 'mock' | 'not-required'
  mockAllowed: 'yes' | 'no'
  realFlow: string
}

export function markdownCells(line: string): string[] {
  if (!line.trim().startsWith('|')) return []
  return line.trim().slice(1, -1).split(/(?<!\\)\|/).map((cell) => cell.replaceAll('\\|', '|').trim())
}

export function assertConcrete(value: string, label: string): void {
  if (!value || /^(?:todo|tbd|pending|n\/?a)$/i.test(value) || /待补|待执行|稍后|占位/.test(value)) {
    throw new Error(`${label} requires concrete evidence`)
  }
}

export function reportSection(content: string, start: string, end: string): string {
  const escape = (value: string): string => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = content.match(new RegExp(`^${escape(start)}\\s*([\\s\\S]+?)\\s*^${escape(end)}\\s*$`, 'm'))
  if (!match) throw new Error(`Execution report section cannot be parsed: ${start}`)
  return match[1]
}

export function isCoreClosure(criterion: string): boolean {
  return /(登录|认证|权限|创建|新增|修改|删除|停用|启用|密码|安全策略|会话|设备|交接|导出|审计|login|auth|permission|create|update|delete|disable|enable|password|security|session|device|handover|offboard|export|audit)/i.test(criterion)
}

export function parseVerificationContracts(report: string, tasks: string[], contractVersion = 2): Map<string, VerificationContract> {
  const columns = contractVersion >= 3 ? 7 : 6
  const rows = reportSection(report, '## Verification Contract', '## Completed Items').split(/\r?\n/).map(markdownCells)
    .filter((cells) => cells.length === columns && cells[0] !== 'Task' && !/^-+$/.test(cells[0]))
  const contracts = new Map<string, VerificationContract>()
  for (const task of tasks) {
    const matches = rows.filter((cells) => cells[0] === task)
    if (matches.length !== 1) throw new Error(`Verification Contract must contain exactly one ${columns}-column row for ${task}; found ${matches.length}`)
    const cells = matches[0]
    cells.slice(1).forEach((value, index) => assertConcrete(value, `${task} verification contract field ${index + 1}`))
    const offset = contractVersion >= 3 ? 1 : 0
    const closureClass = (contractVersion >= 3 ? cells[1].toLowerCase() : 'legacy-inferred') as VerificationContract['closureClass']
    const level = cells[1 + offset].toLowerCase() as VerificationContract['level']
    const browserKind = cells[2 + offset].toLowerCase() as VerificationContract['browserKind']
    const environment = cells[3 + offset].toLowerCase() as VerificationContract['environment']
    const mockAllowed = cells[4 + offset].toLowerCase() as VerificationContract['mockAllowed']
    if (!['non-core', 'core-user', 'core-system', 'legacy-inferred'].includes(closureClass)) throw new Error(`${task} has an invalid closure class: ${cells[1]}`)
    if (!['static', 'unit', 'integration', 'system-real-isolated', 'e2e-real', 'e2e-real-isolated'].includes(level)) throw new Error(`${task} has an invalid verification level: ${cells[1 + offset]}`)
    if (!['real', 'mock', 'not-required'].includes(browserKind)) throw new Error(`${task} has an invalid browser evidence kind: ${cells[2 + offset]}`)
    if (!['isolated', 'shared-readonly', 'mock', 'not-required'].includes(environment)) throw new Error(`${task} has an invalid verification environment: ${cells[3 + offset]}`)
    if (!['yes', 'no'].includes(mockAllowed)) throw new Error(`${task} mock browser allowed must be Yes or No`)
    if (browserKind === 'real' && (!['isolated', 'shared-readonly'].includes(environment) || mockAllowed !== 'no')) throw new Error(`${task} real browser evidence requires isolated/shared-readonly environment and Mock browser allowed = No`)
    if (browserKind === 'mock' && (environment !== 'mock' || mockAllowed !== 'yes')) throw new Error(`${task} mock browser evidence requires Environment = mock and Mock browser allowed = Yes`)
    if (browserKind === 'not-required' && environment !== 'not-required' && level !== 'system-real-isolated') throw new Error(`${task} not-required browser evidence requires Environment = not-required`)
    if (level === 'system-real-isolated' && (browserKind !== 'not-required' || environment !== 'isolated' || mockAllowed !== 'no')) throw new Error(`${task} system-real-isolated requires a real isolated service flow, no browser evidence, and no mock`)
    if (level === 'e2e-real' && (browserKind !== 'real' || mockAllowed !== 'no')) throw new Error(`${task} e2e-real requires real browser evidence and no mock`)
    if (level === 'e2e-real-isolated' && (browserKind !== 'real' || environment !== 'isolated' || mockAllowed !== 'no')) throw new Error(`${task} e2e-real-isolated requires real browser evidence in an isolated environment and no mock`)
    if (closureClass === 'core-user' && level !== 'e2e-real-isolated') throw new Error(`${task} core-user requires e2e-real-isolated`)
    if (closureClass === 'core-system' && level !== 'system-real-isolated') throw new Error(`${task} core-system requires system-real-isolated`)
    contracts.set(task, { closureClass, level, browserKind, environment, mockAllowed, realFlow: cells[5 + offset] })
  }
  return contracts
}

export function systemRealTaskIds(contracts: Map<string, VerificationContract>): string[] {
  return [...contracts.entries()]
    .filter(([, contract]) => contract.level === 'system-real-isolated')
    .map(([task]) => task)
    .sort()
}
