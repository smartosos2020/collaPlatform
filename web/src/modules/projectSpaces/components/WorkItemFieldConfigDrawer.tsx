import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Checkbox,
  Divider,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useMemo } from 'react'
import type { FormInstance } from 'antd'

import type {
  ConfigureWorkItemFieldRequest,
  ConfiguredWorkItemField,
  JsonObject,
  WorkItemFieldOption,
  WorkItemFieldTypeDescriptor,
  WorkItemFieldValidationRule,
} from '../api/workItemFieldsApi'
import type { ConfiguredWorkItemType } from '../api/workItemTypesApi'

type OptionForm = Pick<WorkItemFieldOption, 'optionKey' | 'name' | 'color' | 'status'>
type RuleForm = {
  ruleKey: string
  kind: string
  min?: number
  max?: number
  precision?: number
  scale?: number
  pattern?: string
  format?: string
  allowedValues?: string
}

type ConfigurationForm = {
  required: boolean
  defaultValue: unknown
  validationRules: RuleForm[]
  typeConfig: Record<string, unknown>
  options: OptionForm[]
}

const optionColors = [
  '#2563EB',
  '#7C3AED',
  '#0891B2',
  '#16A34A',
  '#CA8A04',
  '#EA580C',
  '#DC2626',
  '#DB2777',
  '#64748B',
]

export function WorkItemFieldConfigDrawer({
  open,
  field,
  descriptor,
  workItemTypes,
  saving,
  onClose,
  onSave,
}: {
  open: boolean
  field?: ConfiguredWorkItemField
  descriptor?: WorkItemFieldTypeDescriptor
  workItemTypes: ConfiguredWorkItemType[]
  saving: boolean
  onClose: () => void
  onSave: (request: ConfigureWorkItemFieldRequest) => void
}) {
  const [form] = Form.useForm<ConfigurationForm>()
  const watchedOptions = Form.useWatch('options', form)

  useEffect(() => {
    if (!open || !field) return
    form.setFieldsValue({
      required: field.config.required,
      defaultValue: field.config.defaultValue,
      validationRules: field.config.validationRules.map(ruleToForm),
      typeConfig: typeConfigToForm(field.fieldType, field.config.typeConfig),
      options: field.options.map((option) => ({
        optionKey: option.optionKey,
        name: option.name,
        color: option.color,
        status: option.status,
      })),
    })
  }, [field, form, open])

  const activeOptions = useMemo(
    () => (watchedOptions ?? [])
      .filter((option) => option?.status !== 'disabled' && option?.optionKey)
      .map((option) => ({ label: option.name || option.optionKey, value: option.optionKey })),
    [watchedOptions],
  )

  const submit = async () => {
    const values = await form.validateFields()
    if (!field) return
    onSave({
      schemaVersion: field.config.schemaVersion,
      required: values.required ?? false,
      defaultValue: normalizeDefaultValue(field.fieldType, values.defaultValue),
      validationRules: (values.validationRules ?? []).map(ruleFromForm),
      typeConfig: normalizeTypeConfig(field.fieldType, values.typeConfig ?? {}),
      options: (values.options ?? []).map((option, index) => ({
        optionKey: option.optionKey.trim(),
        name: option.name.trim(),
        color: option.color,
        sortOrder: index * 10,
        status: option.status,
      })),
      aggregateVersion: field.aggregateVersion,
    })
  }

  return (
    <Drawer
      className="work-item-field-config-drawer"
      title={field ? `配置字段 · ${field.name}` : '配置字段'}
      open={open}
      width={680}
      destroyOnHidden
      onClose={onClose}
      extra={<Button type="primary" loading={saving} onClick={() => void submit()}>保存配置</Button>}
    >
      {!field || !descriptor ? <Alert type="warning" showIcon message="字段类型目录不可用" /> : (
        <Form<ConfigurationForm> form={form} layout="vertical" requiredMark="optional">
          <section className="work-item-field-form-section" aria-labelledby="field-basic-config">
            <Typography.Title level={5} id="field-basic-config">基础配置</Typography.Title>
            <Typography.Text type="secondary">
              {field.fieldType} · {descriptor.storageKind} · schema v{descriptor.configSchemaVersion}
            </Typography.Text>
            <Form.Item name="required" label="必填">
              <Switch checkedChildren="必填" unCheckedChildren="可选" />
            </Form.Item>
            <DefaultValueEditor
              field={field}
              activeOptions={activeOptions}
            />
          </section>

          {descriptor.supportsOptions ? (
            <>
              <Divider />
              <OptionEditor field={field} form={form} />
            </>
          ) : null}

          {descriptor.validationRuleKinds.length > 0 ? (
            <>
              <Divider />
              <ValidationRuleEditor descriptor={descriptor} />
            </>
          ) : null}

          {Object.keys(descriptor.typeConfigSchema.properties as JsonObject ?? {}).length > 0 ? (
            <>
              <Divider />
              <TypeSpecificEditor
                field={field}
                workItemTypes={workItemTypes}
              />
            </>
          ) : null}

          <Divider />
          <div className="work-item-field-capability-note">
            <div>
              <span>筛选</span>
              <Tag color={descriptor.filterable ? 'green' : 'default'}>{descriptor.filterable ? '支持' : '不支持'}</Tag>
            </div>
            <div>
              <span>排序</span>
              <Tag color={descriptor.sortable ? 'green' : 'default'}>{descriptor.sortable ? '支持' : '不支持'}</Tag>
            </div>
            <div>
              <span>索引能力</span>
              <Tag>{descriptor.indexCapability}</Tag>
            </div>
          </div>
        </Form>
      )}
    </Drawer>
  )
}

function DefaultValueEditor({
  field,
  activeOptions,
}: {
  field: ConfiguredWorkItemField
  activeOptions: Array<{ label: string; value: string }>
}) {
  const label = '默认值'
  if (['user', 'attachment', 'work_item_reference'].includes(field.fieldType)) {
    return (
      <Alert
        type="info"
        showIcon
        message="此类型不在配置页预设对象默认值"
        description={field.fieldType === 'work_item_reference'
          ? '工作项实例将在 S07 引入；当前只配置允许引用的工作项类型。'
          : '用户和文件必须在实际使用时通过对应模块重新解析权限。'}
      />
    )
  }
  if (field.fieldType === 'boolean') {
    return (
      <Form.Item name="defaultValue" label={label} valuePropName="checked">
        <Switch checkedChildren="是" unCheckedChildren="否" />
      </Form.Item>
    )
  }
  if (field.fieldType === 'number') {
    return <Form.Item name="defaultValue" label={label}><InputNumber className="work-item-field-full-input" /></Form.Item>
  }
  if (field.fieldType === 'single_select') {
    return <Form.Item name="defaultValue" label={label}><Select allowClear options={activeOptions} placeholder="不设置默认选项" /></Form.Item>
  }
  if (field.fieldType === 'multi_select') {
    return <Form.Item name="defaultValue" label={label}><Select allowClear mode="multiple" options={activeOptions} placeholder="不设置默认选项" /></Form.Item>
  }
  if (field.fieldType === 'date') {
    return <Form.Item name="defaultValue" label={label}><Input type="date" /></Form.Item>
  }
  if (field.fieldType === 'datetime') {
    return <Form.Item name="defaultValue" label={label}><Input type="datetime-local" /></Form.Item>
  }
  return (
    <Form.Item name="defaultValue" label={label}>
      <Input allowClear maxLength={field.fieldType === 'url' ? 4096 : undefined} />
    </Form.Item>
  )
}

function OptionEditor({
  field,
  form,
}: {
  field: ConfiguredWorkItemField
  form: FormInstance<ConfigurationForm>
}) {
  const persistedOptionKeys = new Set(field.options.map((option) => option.optionKey))
  return (
    <section className="work-item-field-form-section" aria-labelledby="field-options-config">
      <div className="work-item-field-section-heading">
        <div>
          <Typography.Title level={5} id="field-options-config">选项</Typography.Title>
          <Typography.Text type="secondary">键创建后不可复用；不再使用的选项应停用。</Typography.Text>
        </div>
      </div>
      <Form.List name="options">
        {(fields, { add, remove, move }) => (
          <Space direction="vertical" className="work-item-field-form-list">
            {fields.map((item, index) => {
              const optionKey = form.getFieldValue(['options', item.name, 'optionKey']) as string | undefined
              const persisted = optionKey ? persistedOptionKeys.has(optionKey) : false
              return (
                <div className="work-item-field-option-row" key={item.key}>
                  <Form.Item
                    name={[item.name, 'optionKey']}
                    rules={[
                      { required: true, whitespace: true, message: '请输入选项键' },
                      { pattern: /^[a-z][a-z0-9_]*$/, message: '使用小写字母、数字和下划线' },
                    ]}
                  >
                    <Input aria-label={`选项 ${index + 1} 键`} disabled={persisted} placeholder="option_key" />
                  </Form.Item>
                  <Form.Item name={[item.name, 'name']} rules={[{ required: true, whitespace: true }]}>
                    <Input aria-label={`选项 ${index + 1} 名称`} placeholder="显示名称" />
                  </Form.Item>
                  <Form.Item name={[item.name, 'color']} rules={[{ required: true }]}>
                    <Select aria-label={`选项 ${index + 1} 颜色`} options={optionColors.map((color) => ({
                      value: color,
                      label: <Tag color={color}>{color}</Tag>,
                    }))} />
                  </Form.Item>
                  <Form.Item name={[item.name, 'status']} rules={[{ required: true }]}>
                    <Select aria-label={`选项 ${index + 1} 状态`} options={[
                      { value: 'active', label: '使用中' },
                      { value: 'disabled', label: '已停用' },
                    ]} />
                  </Form.Item>
                  <Space size={2} className="work-item-field-row-actions">
                    <Button type="text" aria-label={`上移选项 ${index + 1}`} icon={<ArrowUpOutlined />} disabled={index === 0} onClick={() => move(index, index - 1)} />
                    <Button type="text" aria-label={`下移选项 ${index + 1}`} icon={<ArrowDownOutlined />} disabled={index === fields.length - 1} onClick={() => move(index, index + 1)} />
                    {!persisted ? <Button type="text" danger aria-label={`删除选项 ${index + 1}`} icon={<DeleteOutlined />} onClick={() => remove(index)} /> : null}
                  </Space>
                </div>
              )
            })}
            <Button
              type="dashed"
              icon={<PlusOutlined />}
              disabled={fields.length >= 200}
              onClick={() => add({ optionKey: '', name: '', color: '#2563EB', status: 'active' })}
            >
              添加选项
            </Button>
          </Space>
        )}
      </Form.List>
    </section>
  )
}

function ValidationRuleEditor({ descriptor }: { descriptor: WorkItemFieldTypeDescriptor }) {
  return (
    <section className="work-item-field-form-section" aria-labelledby="field-rules-config">
      <Typography.Title level={5} id="field-rules-config">结构化校验规则</Typography.Title>
      <Typography.Text type="secondary">规则由服务端再次校验；这里不接受脚本、SQL 或自定义表达式。</Typography.Text>
      <Form.List name="validationRules">
        {(fields, { add, remove }) => (
          <Space direction="vertical" className="work-item-field-form-list">
            {fields.map((item, index) => (
              <div className="work-item-field-rule-row" key={item.key}>
                <Form.Item name={[item.name, 'ruleKey']} hidden><Input /></Form.Item>
                <Form.Item name={[item.name, 'kind']} label="规则" rules={[{ required: true }]}>
                  <Select
                    aria-label={`规则 ${index + 1} 类型`}
                    options={descriptor.validationRuleKinds.map((kind) => ({ value: kind, label: ruleLabel(kind) }))}
                  />
                </Form.Item>
                <RuleConfigFields itemName={item.name} />
                <Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除规则 ${index + 1}`} onClick={() => remove(index)} />
              </div>
            ))}
            <Button
              type="dashed"
              icon={<PlusOutlined />}
              disabled={fields.length >= 20}
              onClick={() => add({ kind: descriptor.validationRuleKinds[0], ruleKey: `rule_${fields.length + 1}` })}
            >
              添加规则
            </Button>
          </Space>
        )}
      </Form.List>
    </section>
  )
}

function RuleConfigFields({ itemName }: { itemName: number }) {
  const kind = Form.useWatch(['validationRules', itemName, 'kind'])
  if (kind === 'length') {
    return <Space><Form.Item name={[itemName, 'min']} label="最小长度"><InputNumber min={0} /></Form.Item><Form.Item name={[itemName, 'max']} label="最大长度"><InputNumber min={0} /></Form.Item></Space>
  }
  if (kind === 'number_range') {
    return <Space><Form.Item name={[itemName, 'min']} label="最小值"><InputNumber /></Form.Item><Form.Item name={[itemName, 'max']} label="最大值"><InputNumber /></Form.Item></Space>
  }
  if (kind === 'number_precision') {
    return <Space><Form.Item name={[itemName, 'precision']} label="精度"><InputNumber min={1} max={38} /></Form.Item><Form.Item name={[itemName, 'scale']} label="小数位"><InputNumber min={0} max={38} /></Form.Item></Space>
  }
  if (kind === 'regex') {
    return <Form.Item name={[itemName, 'pattern']} label="正则表达式" rules={[{ required: true }]}><Input maxLength={500} /></Form.Item>
  }
  if (kind === 'format') {
    return <Form.Item name={[itemName, 'format']} label="格式" rules={[{ required: true }]}><Select options={['email', 'hostname', 'ipv4', 'uuid'].map((value) => ({ value }))} /></Form.Item>
  }
  if (kind === 'allowed_values') {
    return <Form.Item name={[itemName, 'allowedValues']} label="允许值" extra="用英文逗号分隔"><Input /></Form.Item>
  }
  return null
}

function TypeSpecificEditor({
  field,
  workItemTypes,
}: {
  field: ConfiguredWorkItemField
  workItemTypes: ConfiguredWorkItemType[]
}) {
  return (
    <section className="work-item-field-form-section" aria-labelledby="field-type-config">
      <Typography.Title level={5} id="field-type-config">类型专属配置</Typography.Title>
      {field.fieldType === 'user' ? (
        <>
          <Form.Item name={['typeConfig', 'allowedSubjectTypes']} label="允许主体" rules={[{ required: true }]}>
            <Checkbox.Group options={[
              { value: 'member', label: '成员' },
              { value: 'department', label: '部门' },
              { value: 'user_group', label: '用户组' },
            ]} />
          </Form.Item>
          <Form.Item
            name={['typeConfig', 'selectionScope']}
            label="选择范围"
            extra="留空表示当前空间可见范围；每行填写 member:UUID、department:UUID 或 user_group:UUID。"
          >
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name={['typeConfig', 'maxSelections']} label="最多选择数" rules={[{ required: true }]}>
            <InputNumber min={1} max={100} />
          </Form.Item>
        </>
      ) : null}
      {field.fieldType === 'date' ? (
        <DateBounds includeTime={false} />
      ) : null}
      {field.fieldType === 'datetime' ? (
        <>
          <Form.Item name={['typeConfig', 'displayTimezone']} label="展示时区" rules={[{ required: true }]}>
            <Select showSearch options={['UTC', 'Asia/Shanghai', 'Asia/Tokyo', 'Europe/London', 'America/New_York'].map((value) => ({ value }))} />
          </Form.Item>
          <Form.Item name={['typeConfig', 'precision']} label="精度" rules={[{ required: true }]}>
            <Select options={['minute', 'second', 'millisecond'].map((value) => ({ value }))} />
          </Form.Item>
          <DateBounds includeTime />
        </>
      ) : null}
      {field.fieldType === 'url' ? (
        <>
          <Form.Item name={['typeConfig', 'allowedSchemes']} label="允许协议" rules={[{ required: true }]}>
            <Checkbox.Group options={[{ value: 'https', label: 'HTTPS' }, { value: 'http', label: 'HTTP' }]} />
          </Form.Item>
          <Form.Item name={['typeConfig', 'maxLength']} label="最大长度" rules={[{ required: true }]}>
            <InputNumber min={1} max={4096} />
          </Form.Item>
          <Alert type="info" showIcon message="URL 凭据始终禁止" description="用户名、密码、危险协议、控制字符和反斜线会被服务端拒绝。" />
        </>
      ) : null}
      {field.fieldType === 'attachment' ? (
        <>
          <Form.Item name={['typeConfig', 'maxFiles']} label="最多文件数" rules={[{ required: true }]}>
            <InputNumber min={1} max={100} />
          </Form.Item>
          <Form.Item name={['typeConfig', 'allowedContentTypes']} label="允许 MIME 类型" extra="留空表示不按 MIME 限制；用英文逗号分隔。">
            <Input placeholder="image/png,application/pdf" />
          </Form.Item>
          <Form.Item name={['typeConfig', 'maxFileSizeBytes']} label="单文件最大字节数" rules={[{ required: true }]}>
            <InputNumber min={1} className="work-item-field-full-input" />
          </Form.Item>
        </>
      ) : null}
      {field.fieldType === 'work_item_reference' ? (
        <>
          <Form.Item name={['typeConfig', 'targetTypeIds']} label="允许引用的工作项类型" rules={[{ required: true }]}>
            <Select
              mode="multiple"
              options={workItemTypes.filter((type) => type.status !== 'retired').map((type) => ({
                value: type.id,
                label: `${type.name} · ${type.typeKey}`,
              }))}
            />
          </Form.Item>
          <Form.Item name={['typeConfig', 'maxReferences']} label="最多引用数" rules={[{ required: true }]}>
            <InputNumber min={1} max={100} />
          </Form.Item>
          <Alert type="info" showIcon message="实例关系尚未启用" description="S04 只保存允许引用的类型和方向；不会创建工作项实例或反向关系。" />
        </>
      ) : null}
    </section>
  )
}

function DateBounds({ includeTime }: { includeTime: boolean }) {
  return (
    <>
      <Form.Item name={['typeConfig', 'defaultStrategy']} label="相对默认值" rules={[{ required: true }]}>
        <Select options={(includeTime ? ['none', 'now'] : ['none', 'today']).map((value) => ({ value }))} />
      </Form.Item>
      <Space className="work-item-field-date-range">
        <Form.Item name={['typeConfig', 'min']} label="最早值"><Input type={includeTime ? 'datetime-local' : 'date'} /></Form.Item>
        <Form.Item name={['typeConfig', 'max']} label="最晚值"><Input type={includeTime ? 'datetime-local' : 'date'} /></Form.Item>
      </Space>
    </>
  )
}

function normalizeDefaultValue(fieldType: ConfiguredWorkItemField['fieldType'], value: unknown) {
  if (['user', 'attachment', 'work_item_reference'].includes(fieldType)) return null
  if (value === '' || value === undefined) return null
  if (fieldType === 'datetime' && typeof value === 'string' && value && !value.endsWith('Z')) {
    return new Date(value).toISOString()
  }
  return value
}

function normalizeTypeConfig(fieldType: ConfiguredWorkItemField['fieldType'], value: Record<string, unknown>) {
  const result = { ...value }
  if (fieldType === 'user') {
    result.selectionScope = parseSubjectScope(value.selectionScope)
  }
  if (fieldType === 'attachment') {
    result.allowedContentTypes = splitValues(value.allowedContentTypes)
  }
  if (fieldType === 'datetime') {
    result.storageTimezone = 'UTC'
    result.min = normalizeInstant(value.min)
    result.max = normalizeInstant(value.max)
  }
  if (fieldType === 'date') {
    result.calendar = 'iso8601'
    result.precision = 'day'
    result.min = emptyToNull(value.min)
    result.max = emptyToNull(value.max)
  }
  if (fieldType === 'url') result.allowCredentials = false
  if (fieldType === 'work_item_reference') {
    result.direction = 'outbound'
    result.relationCapability = 'deferred'
  }
  return result
}

function typeConfigToForm(
  fieldType: ConfiguredWorkItemField['fieldType'],
  value: Record<string, unknown>,
) {
  const result = { ...value }
  if (fieldType === 'user') {
    result.selectionScope = Array.isArray(value.selectionScope)
      ? value.selectionScope
        .map((entry) => {
          if (!isRecord(entry)) return ''
          return `${stringValue(entry.subjectType)}:${stringValue(entry.subjectId)}`
        })
        .filter(Boolean)
        .join('\n')
      : ''
  }
  if (fieldType === 'attachment') {
    result.allowedContentTypes = stringArray(value.allowedContentTypes).join(',')
  }
  if (fieldType === 'datetime') {
    result.min = instantToLocal(value.min)
    result.max = instantToLocal(value.max)
  }
  return result
}

function ruleToForm(rule: WorkItemFieldValidationRule): RuleForm {
  return {
    ruleKey: rule.ruleKey,
    kind: rule.kind,
    min: numberValue(rule.config.min ?? rule.config.minLength),
    max: numberValue(rule.config.max ?? rule.config.maxLength),
    precision: numberValue(rule.config.precision),
    scale: numberValue(rule.config.scale),
    pattern: stringValue(rule.config.pattern),
    format: stringValue(rule.config.format),
    allowedValues: stringArray(rule.config.values).join(','),
  }
}

function ruleFromForm(rule: RuleForm, index: number): WorkItemFieldValidationRule {
  let config: JsonObject = {}
  if (rule.kind === 'length') config = compact({ min: rule.min, max: rule.max })
  if (rule.kind === 'number_range') config = compact({ min: rule.min, max: rule.max })
  if (rule.kind === 'number_precision') config = compact({ precision: rule.precision, scale: rule.scale })
  if (rule.kind === 'regex') config = { pattern: rule.pattern?.trim() ?? '' }
  if (rule.kind === 'format') config = { format: rule.format ?? '' }
  if (rule.kind === 'allowed_values') config = { values: splitValues(rule.allowedValues) }
  return {
    ruleKey: rule.ruleKey || `rule_${index + 1}`,
    kind: rule.kind,
    schemaVersion: 1,
    config,
  }
}

function compact(value: JsonObject) {
  return Object.fromEntries(Object.entries(value).filter(([, item]) => item !== undefined && item !== null))
}

function splitValues(value: unknown) {
  if (Array.isArray(value)) return value.map(String).map((item) => item.trim()).filter(Boolean)
  if (typeof value !== 'string') return []
  return value.split(/[\n,]/).map((item) => item.trim()).filter(Boolean)
}

function parseSubjectScope(value: unknown) {
  return splitValues(value).map((entry) => {
    const separator = entry.indexOf(':')
    return {
      subjectType: separator > 0 ? entry.slice(0, separator).trim() : 'member',
      subjectId: separator > 0 ? entry.slice(separator + 1).trim() : entry,
    }
  })
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function stringArray(value: unknown) {
  return Array.isArray(value) ? value.map(String) : []
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value : undefined
}

function numberValue(value: unknown) {
  return typeof value === 'number' ? value : undefined
}

function normalizeInstant(value: unknown) {
  if (typeof value !== 'string' || !value) return null
  return value.endsWith('Z') ? value : new Date(value).toISOString()
}

function instantToLocal(value: unknown) {
  if (typeof value !== 'string' || !value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 19)
}

function emptyToNull(value: unknown) {
  return value === '' || value === undefined ? null : value
}

function ruleLabel(kind: string) {
  const labels: Record<string, string> = {
    length: '长度范围',
    regex: '正则格式',
    format: '预置格式',
    allowed_values: '允许值',
    number_range: '数值范围',
    number_precision: '数值精度',
  }
  return labels[kind] ?? kind
}
