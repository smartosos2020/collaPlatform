# Colla Desktop Tauri Spike

M8 桌面端优先使用 Tauri 作为轻量壳应用。

验证范围：

- 加载本地 Web：`http://127.0.0.1:5173`。
- 登录保持：沿用 Web localStorage token，后续再评估 OS keychain。
- 系统通知：M8 只验证服务端 push token/通知模型，桌面原生通知在 M9 前补插件。
- 协议唤起：保留 `colla://` deep link，当前由 Web fallback 完成落地页跳转。

Electron 备选触发条件：

- Tauri WebView 在目标系统的兼容性无法满足富文本/表格能力。
- 原生通知、托盘、自动更新、协议注册在 Tauri 上投入明显高于 Electron。
- 团队缺少 Rust/Tauri 维护能力且桌面端复杂度上升。
