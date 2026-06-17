# M8 多端接入 Spike

## 结论

- 桌面端优先 Tauri，作为 Web 常驻入口。
- Electron 暂不推进，只作为 Tauri 不满足时的备选。
- 移动端采用 React Native + Expo Spike，先验证登录、补拉、通知、事项只读和 deep link。
- 服务端把 `deviceId` 写入 access/refresh token，并在 HTTP 与 WebSocket 鉴权时校验设备未撤销。

## Deep Link 规则

| 对象 | Deep link | Web fallback |
| --- | --- | --- |
| 事项 | `colla://issue/{id}` | `/issues/{id}` |
| 文档 | `colla://document/{id}` | `/docs/{id}` |
| 多维表格 | `colla://base/{id}` | `/bases/{id}` |
| 表格记录 | `colla://base_record/{id}` | `/bases/{baseId}/tables/{tableId}/records/{recordId}` |

## 验收口径

- 设备撤销后，同设备 access token 无法继续访问受保护 API。
- 设备撤销后，同设备 refresh token 无法刷新。
- 未被撤销的同账号其他设备不受影响。
- 移动端离线后重新打开，通过 REST 补拉会话未读和通知。
