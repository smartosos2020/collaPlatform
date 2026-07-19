export type ShellBoundary = 'user-workspace' | 'admin-console' | 'shared'

export type FrontendModuleBoundary = {
  boundary: ShellBoundary
  modules: string[]
  owns: string[]
  mustNotOwn: string[]
}

export const frontendModuleBoundaries: FrontendModuleBoundary[] = [
  {
    boundary: 'user-workspace',
    modules: ['dashboard', 'messenger', 'projectSpaces', 'projects', 'knowledgeBases', 'docs', 'bases', 'approvals', 'notifications', 'search', 'devices'],
    owns: ['content consumption', 'collaboration actions', 'personal work state', 'object links'],
    mustNotOwn: ['organization governance', 'permission inspection', 'audit log review', 'global admin configuration'],
  },
  {
    boundary: 'admin-console',
    modules: ['admin'],
    owns: ['organization governance', 'identity administration', 'permission governance', 'application governance', 'audit review', 'admin configuration placeholders'],
    mustNotOwn: ['document reading canvas', 'knowledge content editing', 'message conversations', 'base record editing as a user workflow'],
  },
  {
    boundary: 'shared',
    modules: ['shared', 'platform', 'permissions', 'files', 'auth'],
    owns: ['routing guards', 'auth state', 'primitive UI', 'platform object links', 'resource permission primitives', 'file transfer'],
    mustNotOwn: ['page information architecture', 'admin-only navigation', 'user-only navigation'],
  },
]
