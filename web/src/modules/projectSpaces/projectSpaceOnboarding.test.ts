import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import {
  PROJECT_SPACE_ONBOARDING_STARTING_POINTS,
  canOpenOnboardingStep,
  contextualOnboardingHelp,
  onboardingErrorPresentation,
  onboardingStartingPointValue,
  resolveOnboardingOwnerPath,
  resolveOnboardingStepCopy,
  startingPointCommand,
  type ProjectSpaceOnboardingChecklistItem,
} from './projectSpaceOnboarding'

const step = (overrides: Partial<ProjectSpaceOnboardingChecklistItem> = {}): ProjectSpaceOnboardingChecklistItem => ({
  stepKey: 'member.find-work',
  labelKey: 'member.find-work',
  helpKey: 'member.find-work',
  path: '/project-spaces/space-1/work-items',
  dependencies: [],
  ownerContract: 'work-items',
  status: 'available',
  ...overrides,
})

describe('project space onboarding starting points', () => {
  it('keeps four scenarios plus an honest six-type base space', () => {
    assert.deepEqual(
      PROJECT_SPACE_ONBOARDING_STARTING_POINTS.map((item) => item.key),
      ['development', 'marketing', 'human-resources', 'delivery', 'blank'],
    )
    assert.deepEqual(
      PROJECT_SPACE_ONBOARDING_STARTING_POINTS.map((item) => item.label),
      ['研发模板', '市场模板', 'HR 模板', '交付模板', '基础空间'],
    )
    const blank = PROJECT_SPACE_ONBOARDING_STARTING_POINTS.at(-1)
    assert.match(blank?.summary ?? '', /6 个基础类型/)
    assert.match(blank?.summary ?? '', /不额外安装/)
  })

  it('keeps selection separate from installation and publication', () => {
    for (const point of PROJECT_SPACE_ONBOARDING_STARTING_POINTS) {
      const copy = `${point.summary}${point.effect}`
      assert.match(copy, /选择|不额外安装/)
      assert.doesNotMatch(copy, /已安装|已发布/)
    }
    assert.deepEqual(startingPointCommand('blank'), {
      action: 'select_starting_point',
      startingPoint: 'blank',
    })
    assert.deepEqual(startingPointCommand('development'), {
      action: 'select_starting_point',
      startingPoint: 'scenario',
      scenarioKey: 'development',
    })
    assert.equal(onboardingStartingPointValue({ kind: 'unselected', scenarioKey: null }), undefined)
    assert.equal(onboardingStartingPointValue({ kind: 'blank', scenarioKey: null }), 'blank')
  })
})

describe('project space onboarding capability and route gates', () => {
  it('uses capability, online state, server status and read-only state together', () => {
    assert.equal(canOpenOnboardingStep(step(), ['view_work_items'], true, false), true)
    assert.equal(canOpenOnboardingStep(step(), [], true, false), false)
    assert.equal(canOpenOnboardingStep(step(), ['view_work_items'], false, false), false)
    assert.equal(canOpenOnboardingStep(step({ status: 'blocked' }), ['view_work_items'], true, false), false)
    assert.equal(
      canOpenOnboardingStep(
        step({ stepKey: 'member.create-or-update', labelKey: 'member.create-or-update' }),
        ['view_work_items'],
        true,
        true,
      ),
      false,
    )
  })

  it('only accepts the current project space and notification owner routes', () => {
    assert.equal(
      resolveOnboardingOwnerPath(step(), 'space-1'),
      '/project-spaces/space-1/work-items',
    )
    assert.equal(
      resolveOnboardingOwnerPath(step({ path: '/project-spaces/space-2/work-items' }), 'space-1'),
      '/project-spaces/space-1/work-items',
    )
    assert.equal(
      resolveOnboardingOwnerPath(step({
        stepKey: 'member.notifications',
        labelKey: 'member.notifications',
        path: '/notifications?source=onboarding',
      }), 'space-1'),
      '/notifications?source=onboarding',
    )
  })

  it('falls back to the inline settings owner for work-model and publication steps', () => {
    assert.equal(
      resolveOnboardingOwnerPath(step({
        stepKey: 'configure_work_model',
        labelKey: 'project.onboarding.step.configure_work_model',
        path: '/project-spaces/another-space/types',
      }), 'space-1'),
      '/project-spaces/space-1/settings?panel=work-model',
    )
    for (const stepKey of ['configure_workflow', 'publish_configuration']) {
      assert.equal(
        resolveOnboardingOwnerPath(step({
          stepKey,
          labelKey: `project.onboarding.step.${stepKey}`,
          path: '/project-spaces/another-space/types',
        }), 'space-1'),
        '/project-spaces/space-1/settings?panel=flow-access',
      )
    }
  })

  it('does not convert route visits into business completion', () => {
    const copy = resolveOnboardingStepCopy(step({ status: 'verify_on_owner_api' }))
    assert.equal(copy.label, '找到分配给我的工作')
    assert.match(copy.help, /工作项/)
    assert.doesNotMatch(`${copy.label}${copy.help}${copy.actionLabel}`, /业务已完成/)
  })

  it('provides business copy for every frozen backend checklist key', () => {
    const keys = [
      'choose_starting_point',
      'preview_impact',
      'install_scenario',
      'configure_work_model',
      'configure_fields_and_pages',
      'configure_workflow',
      'configure_permissions',
      'publish_configuration',
      'configure_automation',
      'configure_metrics',
      'invite_members',
      'create_first_work_item',
      'handoff_first_work_item',
      'find_work',
      'create_or_update_work',
      'comment_on_work',
      'attach_file',
      'transition_state',
      'review_notifications',
    ]
    for (const stepKey of keys) {
      const copy = resolveOnboardingStepCopy(step({
        stepKey,
        labelKey: `project.onboarding.step.${stepKey}`,
        helpKey: `project.onboarding.help.${stepKey}`,
      }))
      assert.notEqual(copy.label, '继续下一步', stepKey)
      assert.ok(copy.help.length >= 12, stepKey)
    }
  })
})

describe('project space onboarding contextual and failure help', () => {
  it('explains the current business page without exposing engineering jargon first', () => {
    assert.equal(
      contextualOnboardingHelp('/project-spaces/space-1/work-items').title,
      '工作项',
    )
    assert.match(
      contextualOnboardingHelp('/project-spaces/space-1/types/type-1/fields').next,
      /校验/,
    )
  })

  it('fails closed for hidden spaces and explains CAS refresh safely', () => {
    assert.deepEqual(
      onboardingErrorPresentation({ status: 404 }),
      onboardingErrorPresentation({ status: 403 }),
    )
    assert.match(onboardingErrorPresentation({ status: 409 }).description, /重新加载/)
    assert.doesNotMatch(onboardingErrorPresentation({ status: 404 }).description, /路径|成员名单/)
  })
})
