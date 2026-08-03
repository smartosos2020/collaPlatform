import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import type { WorkItemFieldConfig, WorkItemFieldType } from './api/workItemFieldsApi.ts'
import {
  applyFieldControlPreset,
  availableFieldControlPresets,
  fieldControlLabel,
  fieldControlPreset,
} from './workItemFieldControls.ts'

const baseConfig: WorkItemFieldConfig = {
  schemaVersion: 1,
  required: false,
  defaultValue: null,
  validationRules: [],
  typeConfig: {},
}

describe('work-item field control presets', () => {
  it('keeps the common controls first and only exposes supported storage types', () => {
    const available = availableFieldControlPresets(['text', 'number'] as WorkItemFieldType[])
    assert.deepEqual(available.slice(0, 6).map((item) => item.key), [
      'single_line', 'multiline', 'rich_text', 'number', 'currency', 'percentage',
    ])
    assert.equal(available.every((item) => ['text', 'number'].includes(item.fieldType)), true)
  })

  it('maps rich text and currency controls onto stable base field types', () => {
    const richText = fieldControlPreset('rich_text')!
    const currency = fieldControlPreset('currency')!

    assert.equal(richText.fieldType, 'text')
    assert.equal(applyFieldControlPreset(baseConfig, richText).typeConfig.presentation, 'rich_text')
    assert.equal(currency.fieldType, 'number')
    assert.equal(applyFieldControlPreset(baseConfig, currency).typeConfig.currencyCode, 'CNY')
  })

  it('returns the user-facing control label from a configured field', () => {
    assert.equal(fieldControlLabel('text', { presentation: 'multiline' }), '多行文本')
    assert.equal(fieldControlLabel('number', { presentation: 'rating' }), '评分')
    assert.equal(fieldControlLabel('text', {}), '单行文本')
    assert.equal(fieldControlLabel('number', {}), '数字')
    assert.equal(fieldControlLabel('boolean', {}), '开关')
  })
})
