import type {
  WorkItemFieldConfig,
  WorkItemFieldType,
} from './api/workItemFieldsApi'

export type WorkItemFieldControlPresetKey =
  | 'single_line'
  | 'multiline'
  | 'rich_text'
  | 'email'
  | 'phone'
  | 'number'
  | 'currency'
  | 'percentage'
  | 'duration'
  | 'rating'
  | 'boolean'
  | 'single_select'
  | 'multi_select'
  | 'user'
  | 'date'
  | 'datetime'
  | 'url'
  | 'attachment'
  | 'work_item_reference'

export type WorkItemFieldControlPreset = {
  key: WorkItemFieldControlPresetKey
  label: string
  description: string
  fieldType: WorkItemFieldType
  common: boolean
  typeConfig?: Record<string, unknown>
}

export const WORK_ITEM_FIELD_CONTROL_PRESETS: WorkItemFieldControlPreset[] = [
  preset('single_line', '单行文本', '名称、标题等短文本', 'text', true, { presentation: 'single_line', maxLength: 2000 }),
  preset('multiline', '多行文本', '说明、备注等长文本', 'text', true, { presentation: 'multiline', maxLength: 10000 }),
  preset('rich_text', '富文本', '带格式的详细内容', 'text', true, { presentation: 'rich_text', maxLength: 100000 }),
  preset('number', '数字', '数量、分值等数值', 'number', true, numberConfig('number')),
  preset('currency', '金额', '带币种的金额', 'number', true, numberConfig('currency')),
  preset('percentage', '百分比', '完成率、占比', 'number', true, numberConfig('percentage')),
  preset('single_select', '单选', '从选项中选择一个', 'single_select', true),
  preset('multi_select', '多选', '标签或多个选项', 'multi_select', true),
  preset('user', '人员', '成员、部门或用户组', 'user', true),
  preset('date', '日期', '精确到日期', 'date', true),
  preset('datetime', '日期时间', '精确到时间', 'datetime', true),
  preset('boolean', '开关', '是或否', 'boolean', true),
  preset('email', '邮箱', '电子邮箱地址', 'text', false, { presentation: 'email', maxLength: 320 }),
  preset('phone', '电话', '电话号码', 'text', false, { presentation: 'phone', maxLength: 64 }),
  preset('duration', '时长', '分钟、小时或天', 'number', false, numberConfig('duration')),
  preset('rating', '评分', '限定上限的评分', 'number', false, numberConfig('rating')),
  preset('url', '链接', 'HTTP/HTTPS 地址', 'url', false),
  preset('attachment', '附件', '图片或文件', 'attachment', false),
  preset('work_item_reference', '工作项引用', '关联其他工作项', 'work_item_reference', false),
]

export function fieldControlPreset(key?: WorkItemFieldControlPresetKey) {
  return WORK_ITEM_FIELD_CONTROL_PRESETS.find((item) => item.key === key)
}

export function availableFieldControlPresets(types: WorkItemFieldType[]) {
  const available = new Set(types)
  return WORK_ITEM_FIELD_CONTROL_PRESETS.filter((item) => available.has(item.fieldType))
}

export function applyFieldControlPreset(
  config: WorkItemFieldConfig,
  control: WorkItemFieldControlPreset,
): WorkItemFieldConfig {
  return {
    ...config,
    typeConfig: control.typeConfig
      ? { ...config.typeConfig, ...control.typeConfig }
      : { ...config.typeConfig },
  }
}

export function fieldControlLabel(fieldType: WorkItemFieldType, typeConfig: Record<string, unknown>) {
  const presentation = normalizedPresentation(fieldType, typeConfig)
  return WORK_ITEM_FIELD_CONTROL_PRESETS.find((item) => (
    item.fieldType === fieldType
    && (item.typeConfig?.presentation === presentation || (!item.typeConfig?.presentation && !presentation))
  ))?.label ?? baseFieldTypeLabel(fieldType)
}

export function isCommonFieldControl(fieldType: WorkItemFieldType, typeConfig: Record<string, unknown>) {
  const label = fieldControlLabel(fieldType, typeConfig)
  return WORK_ITEM_FIELD_CONTROL_PRESETS.some((item) => item.common && item.label === label)
}

function normalizedPresentation(fieldType: WorkItemFieldType, typeConfig: Record<string, unknown>) {
  if (typeof typeConfig.presentation === 'string') return typeConfig.presentation
  if (fieldType === 'text') return 'single_line'
  if (fieldType === 'number') return 'number'
  return undefined
}

function preset(
  key: WorkItemFieldControlPresetKey,
  label: string,
  description: string,
  fieldType: WorkItemFieldType,
  common: boolean,
  typeConfig?: Record<string, unknown>,
): WorkItemFieldControlPreset {
  return { key, label, description, fieldType, common, typeConfig }
}

function numberConfig(presentation: 'number' | 'currency' | 'percentage' | 'duration' | 'rating') {
  return {
    presentation,
    currencyCode: 'CNY',
    precision: presentation === 'percentage' ? 2 : 0,
    durationUnit: 'hours',
    ratingMax: 5,
  }
}

function baseFieldTypeLabel(type: WorkItemFieldType) {
  return ({
    text: '文本',
    number: '数字',
    boolean: '开关',
    single_select: '单选',
    multi_select: '多选',
    user: '人员',
    date: '日期',
    datetime: '日期时间',
    url: '链接',
    attachment: '附件',
    work_item_reference: '工作项引用',
  } as const)[type]
}
