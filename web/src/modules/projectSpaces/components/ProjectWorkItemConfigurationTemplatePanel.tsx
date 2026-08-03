import {
  AppstoreAddOutlined,
  DisconnectOutlined,
  MergeOutlined,
  PlusOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Form,
  Input,
  List,
  Modal,
  Radio,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState } from 'react'

import {
  applyWorkItemConfigurationTemplateUpgrade,
  createWorkItemConfigurationTemplate,
  detachWorkItemConfigurationTemplate,
  getWorkItemConfigurationTemplateInstallation,
  installWorkItemConfigurationTemplate,
  listWorkItemConfigurationTemplates,
  previewWorkItemConfigurationTemplateUpgrade,
  workItemConfigurationDraftKeys,
  workItemConfigurationTemplateKeys,
  workItemConfigurationVersionKeys,
  type ConfigurationTemplate,
  type ConfigurationVersion,
  type TemplateUpgradePreview,
  type WorkItemConfigurationDraft,
} from '../api/workItemConfigurationApi'
import { errorMessage } from '../projectSpaceView'

type Resolution = 'local' | 'upstream'

export function ProjectWorkItemConfigurationTemplatePanel({
  spaceId,
  typeId,
  readOnly,
  draft,
  currentVersion,
}: {
  spaceId: string
  typeId: string
  readOnly: boolean
  draft: WorkItemConfigurationDraft
  currentVersion?: ConfigurationVersion
}) {
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>()
  const [preview, setPreview] = useState<TemplateUpgradePreview>()
  const [resolutions, setResolutions] = useState<Record<string, Resolution>>({})
  const [createOpen, setCreateOpen] = useState(false)
  const [form] = Form.useForm<{ templateKey: string; name: string; description: string }>()

  const catalogQuery = useQuery({
    queryKey: workItemConfigurationTemplateKeys.catalog(spaceId),
    queryFn: () => listWorkItemConfigurationTemplates(spaceId),
    retry: false,
    refetchOnWindowFocus: false,
  })
  const installationQuery = useQuery({
    queryKey: workItemConfigurationTemplateKeys.installation(spaceId, typeId),
    queryFn: () => getWorkItemConfigurationTemplateInstallation(spaceId, typeId),
    retry: false,
    refetchOnWindowFocus: false,
  })
  const activeTemplates = useMemo(
    () => (catalogQuery.data ?? []).filter((template) => template.status === 'active'),
    [catalogQuery.data],
  )
  const installedTemplate = catalogQuery.data?.find(
    (template) => template.id === installationQuery.data?.templateId,
  )
  const selectedTemplate = activeTemplates.find((template) => template.id === selectedTemplateId)
    ?? activeTemplates[0]

  const refresh = async () => {
    setPreview(undefined)
    setResolutions({})
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: workItemConfigurationTemplateKeys.catalog(spaceId),
      }),
      queryClient.invalidateQueries({
        queryKey: workItemConfigurationTemplateKeys.installation(spaceId, typeId),
      }),
      queryClient.invalidateQueries({
        queryKey: workItemConfigurationDraftKeys.detail(spaceId, typeId),
      }),
      queryClient.invalidateQueries({
        queryKey: workItemConfigurationVersionKeys.draftDiff(
          spaceId,
          typeId,
          draft.configHash,
        ),
      }),
    ])
  }

  const installMutation = useMutation({
    mutationFn: (template: ConfigurationTemplate) => installWorkItemConfigurationTemplate(
      spaceId,
      typeId,
      template.id,
      template.currentVersion.id,
      draft.aggregateVersion,
    ),
    onSuccess: async (result) => {
      message.success(result.replayed ? '模板安装请求已重放' : '模板已复制到配置草稿')
      await refresh()
    },
    onError: (error) => message.error(errorMessage(error, '安装模板失败')),
  })
  const previewMutation = useMutation({
    mutationFn: () => {
      if (!installedTemplate) throw new Error('模板来源不可用')
      return previewWorkItemConfigurationTemplateUpgrade(
        spaceId,
        typeId,
        installedTemplate.currentVersion.id,
        resolutions,
      )
    },
    onSuccess: (value) => {
      setPreview(value)
      setResolutions((current) => {
        const next = { ...current }
        value.conflicts.forEach((conflict) => {
          next[conflict.keyPath] ??= 'local'
        })
        return next
      })
    },
    onError: (error) => message.error(errorMessage(error, '模板升级预览失败')),
  })
  const upgradeMutation = useMutation({
    mutationFn: () => {
      if (!preview || !installationQuery.data) throw new Error('请先生成升级预览')
      return applyWorkItemConfigurationTemplateUpgrade(
        spaceId,
        typeId,
        preview.upstreamVersionId,
        draft.aggregateVersion,
        installationQuery.data.aggregateVersion,
        resolutions,
      )
    },
    onSuccess: async () => {
      message.success('模板升级已合并到配置草稿')
      await refresh()
    },
    onError: (error) => message.error(errorMessage(error, '应用模板升级失败')),
  })
  const detachMutation = useMutation({
    mutationFn: () => detachWorkItemConfigurationTemplate(
      spaceId,
      typeId,
      installationQuery.data?.aggregateVersion ?? -1,
    ),
    onSuccess: async () => {
      message.success('已解除模板关联，本地草稿保持不变')
      await refresh()
    },
    onError: (error) => message.error(errorMessage(error, '解除模板关联失败')),
  })
  const createMutation = useMutation({
    mutationFn: (values: { templateKey: string; name: string; description: string }) => {
      if (!currentVersion) throw new Error('请先发布完整配置版本')
      return createWorkItemConfigurationTemplate(
        spaceId,
        typeId,
        currentVersion.id,
        values,
      )
    },
    onSuccess: async (template) => {
      message.success(`工作区模板「${template.name}」已创建`)
      setCreateOpen(false)
      form.resetFields()
      await queryClient.invalidateQueries({
        queryKey: workItemConfigurationTemplateKeys.catalog(spaceId),
      })
    },
    onError: (error) => message.error(errorMessage(error, '创建模板失败')),
  })

  const attached = installationQuery.data?.status === 'attached'
  const upgradeAvailable = attached
    && installedTemplate
    && installationQuery.data?.upstreamVersionId !== installedTemplate.currentVersion.id

  return (
    <section className="work-item-template-panel" aria-label="配置模板">
      <div className="work-item-template-heading">
        <Space wrap>
          <AppstoreAddOutlined />
          <Typography.Text strong>配置模板</Typography.Text>
          {installationQuery.data ? (
            <Tag color={attached ? 'processing' : 'default'}>
              {attached ? '已关联' : '已解绑'}
            </Tag>
          ) : null}
          {upgradeAvailable ? <Tag color="warning">有可用升级</Tag> : null}
        </Space>
        {catalogQuery.isError ? (
          <Alert type="error" showIcon message="模板目录加载失败" />
        ) : (
          <div className="work-item-template-controls">
            <Select
              aria-label="选择配置模板"
              loading={catalogQuery.isLoading}
              value={selectedTemplate?.id}
              onChange={setSelectedTemplateId}
              options={activeTemplates.map((template) => ({
                value: template.id,
                label: `${template.name} · v${template.currentVersion.versionNumber}`,
              }))}
              placeholder="选择平台或工作区模板"
            />
            <Button
              icon={<AppstoreAddOutlined />}
              disabled={readOnly || !selectedTemplate}
              loading={installMutation.isPending}
              onClick={() => selectedTemplate && modal.confirm({
                title: `安装模板「${selectedTemplate.name}」？`,
                content: '模板快照会复制到当前配置草稿；之后本地编辑不会反向修改模板。',
                okText: '安装到草稿',
                cancelText: '取消',
                onOk: () => installMutation.mutateAsync(selectedTemplate),
              })}
            >
              安装
            </Button>
            <Button
              icon={<SyncOutlined />}
              disabled={readOnly || !upgradeAvailable}
              loading={previewMutation.isPending}
              onClick={() => previewMutation.mutate()}
            >
              预览升级
            </Button>
            <Button
              danger
              icon={<DisconnectOutlined />}
              disabled={readOnly || !attached}
              loading={detachMutation.isPending}
              onClick={() => modal.confirm({
                title: '解除模板关联？',
                content: '本地草稿、配置版本和最后一次来源摘要都会保留，后续不再提示上游升级。',
                okText: '解除关联',
                okButtonProps: { danger: true },
                cancelText: '取消',
                onOk: () => detachMutation.mutateAsync(),
              })}
            >
              解绑
            </Button>
            <Button
              icon={<PlusOutlined />}
              disabled={readOnly || !currentVersion?.completeSnapshot}
              onClick={() => setCreateOpen(true)}
            >
              保存为模板
            </Button>
          </div>
        )}
      </div>

      {attached && installedTemplate ? (
        <Typography.Text type="secondary">
          来源：{installedTemplate.scope === 'platform' ? '平台模板' : '工作区模板'} / {installedTemplate.name}
          {' '}· 当前上游 v{installedTemplate.currentVersion.versionNumber}
        </Typography.Text>
      ) : null}

      {preview ? (
        <div className="work-item-template-conflicts" aria-label="模板升级冲突">
          <Space>
            <MergeOutlined />
            <Typography.Text strong>升级预览</Typography.Text>
            <Tag color={preview.conflicts.length > 0 ? 'warning' : 'success'}>
              {preview.conflicts.length} 个冲突
            </Tag>
          </Space>
          <List
            size="small"
            dataSource={preview.conflicts}
            locale={{ emptyText: '无冲突，可直接应用上游变化' }}
            renderItem={(conflict) => (
              <List.Item
                extra={(
                  <Radio.Group
                    size="small"
                    value={resolutions[conflict.keyPath] ?? 'local'}
                    onChange={(event) => setResolutions((current) => ({
                      ...current,
                      [conflict.keyPath]: event.target.value as Resolution,
                    }))}
                    options={[
                      { label: '保留本地', value: 'local' },
                      { label: '采用上游', value: 'upstream' },
                    ]}
                  />
                )}
              >
                <List.Item.Meta
                  title={conflict.keyPath}
                  description={conflict.reason === 'delete_or_modify' ? '删除与修改冲突' : '双方同时修改'}
                />
              </List.Item>
            )}
          />
          <Button
            type="primary"
            icon={<MergeOutlined />}
            loading={upgradeMutation.isPending}
            disabled={readOnly || !preview.upgradeAvailable}
            onClick={() => upgradeMutation.mutate()}
          >
            应用升级
          </Button>
        </div>
      ) : null}

      <Modal
        title="保存为工作区模板"
        open={createOpen}
        okText="创建模板"
        cancelText="取消"
        confirmLoading={createMutation.isPending}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.validateFields().then((values) => createMutation.mutate(values))}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="模板名称"
            name="name"
            rules={[{ required: true, message: '请输入模板名称' }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item
            label="模板代码"
            name="templateKey"
            rules={[
              { required: true, message: '请输入模板代码' },
              { pattern: /^[a-z][a-z0-9_-]{1,95}$/, message: '仅支持小写字母、数字、- 和 _' },
            ]}
          >
            <Input maxLength={96} />
          </Form.Item>
          <Form.Item label="说明" name="description" initialValue="">
            <Input.TextArea maxLength={2000} rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  )
}
