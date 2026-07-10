export type UserKnowledgeContentState = 'available' | 'restricted' | 'missing'

export type UserKnowledgeNodeView = {
  id: string
  title: string
  path: string
  contentState: UserKnowledgeContentState
  objectType?: string
}

export type UserKnowledgeEditorMode = 'read' | 'edit'
