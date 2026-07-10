export type ApiBoundaryKind = 'user-collaboration' | 'admin-governance' | 'shared-platform' | 'compatibility'

export type ApiMigrationAction = 'keep' | 'wrap-facade' | 'migrate-caller' | 'deprecate' | 'remove-after-compat'

export type ApiBoundaryRule = {
  prefix: string
  boundary: ApiBoundaryKind
  owner: string
  dtoRule: string
  permissionRule: string
  errorRule: string
  migrationAction: ApiMigrationAction
}

export const apiBoundaryRules: ApiBoundaryRule[] = [
  {
    prefix: '/workspace',
    boundary: 'user-collaboration',
    owner: 'dashboard',
    dtoRule: 'UserWorkspace*View or workspace module summaries',
    permissionRule: 'current user visibility and personal work state',
    errorRule: 'focus on unavailable or unactionable personal content',
    migrationAction: 'keep',
  },
  {
    prefix: '/conversations',
    boundary: 'user-collaboration',
    owner: 'messenger',
    dtoRule: 'Conversation*, Message*, UserMessage*View for future facades',
    permissionRule: 'conversation membership and message action permissions',
    errorRule: 'focus on conversation not visible, message revoked, or action unavailable',
    migrationAction: 'keep',
  },
  {
    prefix: '/projects',
    boundary: 'user-collaboration',
    owner: 'projects',
    dtoRule: 'Project*, Issue*, UserProject*View for future facades',
    permissionRule: 'project membership and issue workflow permissions',
    errorRule: 'focus on issue unavailable or workflow action not allowed',
    migrationAction: 'keep',
  },
  {
    prefix: '/issues',
    boundary: 'user-collaboration',
    owner: 'projects',
    dtoRule: 'Issue* user workflow DTO',
    permissionRule: 'issue visibility, transition, comment and attachment permissions',
    errorRule: 'focus on issue unavailable or workflow action not allowed',
    migrationAction: 'keep',
  },
  {
    prefix: '/knowledge-bases',
    boundary: 'user-collaboration',
    owner: 'knowledgeBases',
    dtoRule: 'UserKnowledge*View for content path; AdminKnowledge*View only under /admin',
    permissionRule: 'space membership, content ACL and space management actions',
    errorRule: 'focus on content unavailable, object missing, permission request available',
    migrationAction: 'wrap-facade',
  },
  {
    prefix: '/docs',
    boundary: 'compatibility',
    owner: 'docs editor foundation',
    dtoRule: 'Document* remains editor/compat DTO; new product DTO must wrap it',
    permissionRule: 'document ACL and editor action permissions',
    errorRule: 'focus on legacy deep link, editor state, block/comment/version action',
    migrationAction: 'deprecate',
  },
  {
    prefix: '/bases',
    boundary: 'user-collaboration',
    owner: 'bases',
    dtoRule: 'Base*, BaseRecord*, UserBase*View for future facades',
    permissionRule: 'base ACL, record edit and view permissions',
    errorRule: 'focus on table/record unavailable or edit not allowed',
    migrationAction: 'keep',
  },
  {
    prefix: '/base-records',
    boundary: 'user-collaboration',
    owner: 'bases',
    dtoRule: 'BaseRecord* user DTO',
    permissionRule: 'base record access through parent base ACL',
    errorRule: 'focus on record unavailable or action not allowed',
    migrationAction: 'keep',
  },
  {
    prefix: '/approvals',
    boundary: 'user-collaboration',
    owner: 'approvals',
    dtoRule: 'Approval* user workflow DTO',
    permissionRule: 'applicant, approver and participant permissions',
    errorRule: 'focus on approval unavailable or operation not allowed',
    migrationAction: 'keep',
  },
  {
    prefix: '/notifications',
    boundary: 'user-collaboration',
    owner: 'notifications',
    dtoRule: 'Notification* personal DTO',
    permissionRule: 'current user notification ownership',
    errorRule: 'focus on notification unavailable or already processed',
    migrationAction: 'keep',
  },
  {
    prefix: '/search',
    boundary: 'user-collaboration',
    owner: 'search',
    dtoRule: 'Search* current-user result DTO',
    permissionRule: 'result must be filtered by current user visibility',
    errorRule: 'focus on no results or object unavailable',
    migrationAction: 'wrap-facade',
  },
  {
    prefix: '/admin/application-governance',
    boundary: 'admin-governance',
    owner: 'admin',
    dtoRule: 'AdminApplication*GovernanceView with policy, metric, audit and deep-link fields',
    permissionRule: 'admin shell access required; no user collaboration objects are exposed as page bodies',
    errorRule: 'explain governance boundary and missing admin access',
    migrationAction: 'wrap-facade',
  },
  {
    prefix: '/admin/search-governance',
    boundary: 'admin-governance',
    owner: 'admin',
    dtoRule: 'AdminGovernanceSearch* only; never reuse Search* user content DTO for governance results',
    permissionRule: 'admin shell access required; ordinary user search cannot return governance objects',
    errorRule: 'explain missing admin access or empty governance matches',
    migrationAction: 'wrap-facade',
  },
  {
    prefix: '/admin',
    boundary: 'admin-governance',
    owner: 'admin',
    dtoRule: 'Admin*View, Admin*Summary, *GovernanceView',
    permissionRule: 'admin role or specific admin permission code required',
    errorRule: 'include policy, source, audit and governance reason when available',
    migrationAction: 'wrap-facade',
  },
  {
    prefix: '/platform',
    boundary: 'shared-platform',
    owner: 'platform',
    dtoRule: 'PlatformObject* and PermissionExplanation primitives only',
    permissionRule: 'resolver plus resource permission decision',
    errorRule: 'normalize object unavailable, forbidden, deleted and invalid states',
    migrationAction: 'keep',
  },
  {
    prefix: '/resource-permissions',
    boundary: 'shared-platform',
    owner: 'permissions',
    dtoRule: 'ResourcePermission* primitives; user/admin wrappers decide presentation',
    permissionRule: 'resource manage/share permission, admin inspection only in admin facade',
    errorRule: 'include source and required/current permission level',
    migrationAction: 'wrap-facade',
  },
  {
    prefix: '/files',
    boundary: 'shared-platform',
    owner: 'files',
    dtoRule: 'FileMetadata and upload/download commands',
    permissionRule: 'target object upload/download permission where target is known',
    errorRule: 'focus on file unavailable, upload rejected or target unauthorized',
    migrationAction: 'keep',
  },
]

export const userViewDtoRule = 'Use User*View or module-specific collaboration summaries; do not include admin governance fields by default.'
export const adminViewDtoRule = 'Use Admin*View, Admin*Summary or *GovernanceView; include policy, source, audit and management action fields explicitly.'
export const commandDtoRule = 'Use *Command, *Request or action-specific payloads for writes; do not reuse read DTOs as commands.'
export const internalModelRule = 'Keep domain/internal models behind services and map them into user/admin DTOs at API facade boundaries.'
