import {
  BellOutlined,
  CheckSquareOutlined,
  DatabaseOutlined,
  HomeOutlined,
  MessageOutlined,
  ProjectOutlined,
  ReadOutlined,
  SearchOutlined,
} from '@ant-design/icons'

export const userNavEntries = [
  { key: '/', icon: <HomeOutlined />, label: '工作台' },
  { key: '/im', icon: <MessageOutlined />, label: '消息' },
  { key: '/project-spaces', icon: <ProjectOutlined />, label: '项目' },
  { key: '/knowledge-bases', icon: <ReadOutlined />, label: '知识库' },
  { key: '/bases', icon: <DatabaseOutlined />, label: '表格' },
  { key: '/approvals', icon: <CheckSquareOutlined />, label: '审批' },
  { key: '/notifications', icon: <BellOutlined />, label: '通知' },
  { key: '/search', icon: <SearchOutlined />, label: '搜索' },
]

export const mobileUserNavEntries = userNavEntries.filter((item) =>
  ['/im', '/project-spaces', '/knowledge-bases', '/bases', '/notifications'].includes(item.key),
)
