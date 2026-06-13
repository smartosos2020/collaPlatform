# Colla Mobile Expo Spike

M8 移动端 Spike 目标是验证协议和轻量入口，不追求完整移动产品。

已覆盖：

- 账号密码登录，设备类型使用 `android`。
- 保存 access/refresh token 与移动设备指纹。
- 登记 fake push token。
- 补拉会话列表和通知列表。
- 只读事项详情。
- 监听 `colla://issue/{id}` deep link。

本地运行前需要安装依赖：

```bash
pnpm --dir mobile/expo-spike install
pnpm --dir mobile/expo-spike start
```
