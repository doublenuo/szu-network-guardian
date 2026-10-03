# SZU 网络守护 · iPhone 端

这是一个原生 SwiftUI iOS 客户端，复用桌面端和 Android 端的深圳大学校园网认证协议：

- 教学 / 办公区：深澜 SRun
- 宿舍区：eportal
- 自动检测外网，掉线后尝试认证
- 账号密码使用 iOS Keychain 保存
- 支持手动检测、系统后台刷新和本地通知

## iOS 的后台限制

iOS 不允许第三方 App 像 Android 前台服务一样无限期常驻后台。因此本版本不能保证 24 小时持续轮询：用户打开 App 或系统执行 `BGAppRefreshTask` 时会检测并重连。若需要最可靠的持续守护，请使用 Android 版本。

## 使用 Xcode

1. 使用 macOS + Xcode 15 或更高版本打开 `ios/SZUNetworkGuardian.xcodeproj`。
2. 在 Signing & Capabilities 中选择自己的 Apple Developer Team，并修改 Bundle Identifier。
3. 选择真实 iPhone 或模拟器运行。认证协议必须在实际校园 Wi-Fi 环境中测试。
4. 首次打开后填写账号、密码和网络区域，点击“保存并检测”。
5. 允许通知，并在系统设置中允许该 App 的后台 App 刷新。

也可以直接把 `ios/SZUNetworkGuardian` 目录中的 Swift 文件加入一个新的 SwiftUI iOS App 工程。

## 注意

校园网通常需要先连接 `SZU_WLAN`。本 App 负责 Portal 认证，不负责打开或切换 Wi-Fi。

项目为调试用途，使用了与桌面端一致的自签名证书兼容策略；不要将其作为通用网络库复用到其他项目。
