import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..', '..')
const contractPath = resolve(
  root,
  'tools/capacity/config/s21-engineering-readiness.v1.json',
)
const contract = JSON.parse(readFileSync(contractPath, 'utf8'))
const fail = (message) => {
  throw new Error(`S21 engineering readiness contract invalid: ${message}`)
}

if (contract.schemaVersion !== 'colla.s21-engineering-readiness/v1') {
  fail('unexpected schemaVersion')
}
if (contract.productionSloClaim !== false) {
  fail('local evidence must not claim a production SLO')
}
if (contract.dataPolicy?.syntheticOnly !== true
    || contract.dataPolicy?.containsPersonalData !== false) {
  fail('only synthetic non-personal data is allowed')
}
if (JSON.stringify(contract.canonicalDomains) !== JSON.stringify([
  'project_space',
  'work_item',
])) {
  fail('only canonical project product domains are allowed')
}
if (!contract.prohibitedActiveDomains?.includes('project')
    || !contract.prohibitedActiveDomains?.includes('issue')) {
  fail('retired product domains must be explicitly prohibited')
}
const expectedScenarios = ['development', 'marketing', 'hr', 'delivery']
if (contract.scenarios?.length !== expectedScenarios.length
    || contract.scenarios.some((scenario, index) =>
      scenario.key !== expectedScenarios[index]
      || scenario.workItemCount !== 250
      || !Array.isArray(scenario.operations)
      || scenario.operations.length < 5)) {
  fail('the four deterministic scenario contracts are incomplete')
}
if (contract.legacyCapacityV1?.allowedForS21Evidence !== false
    || contract.legacyCapacityV1?.status !== 'history-only') {
  fail('legacy capacity-v1 must remain excluded from S21 evidence')
}
if (!Object.values(contract.localBudgets ?? {}).every(
  (value) => Number.isFinite(value) && value >= 0,
)) {
  fail('local budgets must be finite non-negative numbers')
}

console.log(
  `S21 readiness contract PASS: scenarios=${expectedScenarios.join(',')}; `
  + 'domains=project_space,work_item; productionSloClaim=false',
)
