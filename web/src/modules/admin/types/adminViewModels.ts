export type AdminStatus = 'active' | 'disabled'

export type AdminListView<TItem> = {
  items: TItem[]
  total?: number
}

export type AdminEntitySummary = {
  id: string
  name: string
  status: AdminStatus | string
}

export type AdminTableAction = {
  key: string
  label: string
  danger?: boolean
}
