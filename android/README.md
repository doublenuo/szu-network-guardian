# SZU 网络守护 · Android 端

桌面端（Tauri + Python）逻辑的 Kotlin 原生移植，复用同一套校园网认证协议：

- 教学 / 办公区：深澜 SRun 认证（`xencode` / 自定义 base64 / HMAC-MD5 / SHA1 校验）
- 宿舍区：`http://172.30.255.42:801/eportal/portal/login/`
- 连通性检测：百度 favicon + Microsoft Connect Test

Android 上没有系统托盘，用**前台服务常驻通知栏**做等价物；开机自启用 **BOOT_COMPLETED 广播**实现。

## 目录结构

```
android/
├── app/src/main/java/com/weno/szuguardian/
│   ├── MainActivity.kt      # Compose 界面
│   ├── GuardianService.kt   # 前台服务：检测循环 + 常驻通知
│   ├── GuardianState.kt     # 服务与界面共享状态
│   ├── BootReceiver.kt      # 开机 / 应用更新后自启
│   ├── data/ConfigStore.kt  # 配置存储（SharedPreferences）
│   └── network/             # SrunClient / NetworkClient / Http
└── gradlew                  # 构建脚本
```

## 构建

### 方式一：终端

1. 准备 JDK 17+ 与 Android SDK（platforms;android-35、build-tools;35.0.0），并设置：

   ```bash
   export ANDROID_HOME=$HOME/android-sdk
   export ANDROID_SDK_ROOT=$ANDROID_HOME
   ```

2. 构建调试包：

   ```bash
   cd android
   ./gradlew assembleDebug     # 产物：app/build/outputs/apk/debug/app-debug.apk
   ./gradlew assembleRelease   # 未签名 release 包
   ```

   若 Gradle 发行包下载过慢/失败，把 `gradle/wrapper/gradle-wrapper.properties` 里的
   `distributionUrl` 换成镜像，例如：

   ```
   distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip
   ```

### 方式二：Android Studio

`File > Open` 选择本仓库的 `android` 目录，等待 Gradle Sync 后直接 Run。

## 安装后必做

1. 打开应用，填写校园网账号密码、选择区域、设置检测间隔，点「开始守护」。
2. 允许通知权限（Android 13+ 首次启动会弹窗），否则通知栏常驻状态不可见。
3. 需要长期后台守护时，点「关闭电池优化」并在系统弹窗里允许，否则系统可能冻结后台。
4. 勾选「开机自动启动」后，重启手机会自动拉起守护。

## 行为说明

- 返回键 / 关闭界面只退到后台，服务继续守护（对应桌面端的「关闭到托盘」）。
- 通知栏提供「立即检测」「停止守护」两个操作，点击通知回到主界面。
- 点「停止」或通知栏「停止守护」才会真正结束后台服务。

## 安全说明

账号密码保存在应用私有 `SharedPreferences`（`MODE_PRIVATE`，其他应用不可读）。如需更高强度，可改为
`EncryptedSharedPreferences` 或 Android Keystore 加密。

SRun 协议实现移植自仓库内 `szu_guardian/srun.py`，归属见根目录 `THIRD_PARTY_NOTICES.md`。
