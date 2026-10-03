# SZU Network Guardian

[![Build packages](https://github.com/doublenuo/szu-network-guardian/actions/workflows/build-windows.yml/badge.svg)](https://github.com/doublenuo/szu-network-guardian/actions/workflows/build-windows.yml)
[![Python 3.10+](https://img.shields.io/badge/Python-3.10%2B-3776AB?logo=python&logoColor=white)](https://www.python.org/)
[![Windows](https://img.shields.io/badge/Windows-10%20%7C%2011-0078D4?logo=windows11&logoColor=white)](https://www.microsoft.com/windows)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

一个简洁、轻量的深圳大学校园网断线监控与自动重连工具，支持 Windows、Linux 和 Android。

桌面端现在使用 Tauri 2 + React + TypeScript 构建。校园网认证协议继续由经过测试的 Python 核心负责，Tauri 通过本地 JSON 桥接调用它；这样 Linux 界面使用 WebKitGTK 原生渲染，不再依赖 Tkinter 字体和 X11 托盘行为。

支持新版教学/办公区深澜 SRun 认证、宿舍区 ePortal 认证、系统托盘、开机自启和安全凭据存储。

## 功能特性

- 简约的 Windows / Linux 图形界面
- 手动选择教学/办公区或宿舍区，避免校园网网关互通造成误判
- 提供实验性的自动顺序尝试模式，优先尝试教学区
- 定时检测直连网络状态，避免代理造成在线误判
- 可强制通过校园网物理网卡直连，绕过 Clash、Mihomo、V2Ray 等代理的 TUN/Fake-IP
- 教学/办公区优先核验 SRun 认证会话；即使百度等白名单网页可打开，认证失效也会立即重连
- 使用两个独立且不可缓存的外网请求复核网络，避免单个检测页造成假在线
- 教学/办公区使用 SRun challenge 加密认证
- 宿舍区使用 ePortal 认证接口
- 仅在断网时尝试登录，不会在联网正常时重复认证
- 认证成功后分阶段复查外网，避免过早报告失败
- 最小化或关闭窗口后进入系统托盘继续运行
- 托盘菜单支持打开界面、立即检测和退出
- 支持当前用户开机自启，无需管理员权限（Windows 注册表 / Linux `.desktop`）
- Windows 使用 DPAPI 保存密码；Linux 优先使用桌面 Secret Service，未安装时使用仅当前用户可读的配置文件
- 界面仅保留最近 3 小时日志
- 完整日志按天保存，自动删除 7 天前的日志
- 支持 PyInstaller 单文件 EXE 和 GitHub Actions 自动构建

## 系统要求

- Windows 10 / 11 或 Ubuntu 22.04+ 等主流 Linux 桌面发行版
- 运行源码时需要 Python 3.10 或更高版本
- 已连接 `SZU_WLAN` 或深圳大学校园有线网络

> [!NOTE]
> 本程序负责网络链路建立后的校园网认证。如果关闭 Wi-Fi、拔出网线或在 Windows 中主动断开无线网络，请先恢复物理网络连接。建议为 `SZU_WLAN` 开启“自动连接”。

## 快速开始

### 方式一：下载 Release（推荐）

不需要安装 Python，适合大多数用户：

1. 打开 [Releases 页面](https://github.com/doublenuo/szu-network-guardian/releases/latest)。
2. 下载最新版 `SZU-Network-Guardian-v*.exe`。
3. 双击 EXE 即可运行，无需安装。

### 首次使用

1. 输入校园网账号和统一身份认证密码。
2. 按电脑的实际位置选择区域：实验室电脑选择“教学 / 办公区”，宿舍电脑选择“宿舍区”。不建议长期无人值守的电脑使用实验性自动模式。
3. 设置检测间隔，默认 1 分钟，可按需调整。
4. 根据需要勾选“开机自动启动”。
5. 点击“开始守护”。
6. 关闭或最小化窗口时程序不会退出，而是隐藏到系统托盘继续监控（单击/双击托盘图标或选择“打开主界面”恢复）。
7. 真正退出：托盘菜单“退出程序”，或界面右下角的“退出程序”按钮。
   使用代理软件时保持“检测和认证强制直连（推荐）”处于勾选状态。

### 方式二：使用源码运行

```bash
git clone https://github.com/doublenuo/szu-network-guardian.git
cd szu-network-guardian
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r requirements.txt
python main.py
```

### Tauri 桌面端（推荐 Linux）

Ubuntu 24.04 先安装 Tauri 的 WebKitGTK 开发依赖：

```bash
sudo apt install libwebkit2gtk-4.1-dev \
  build-essential curl wget file libssl-dev libayatana-appindicator3-dev \
  librsvg2-dev
```

然后运行 React/Tauri 前端：

```bash
npm install
npm run tauri dev
```

构建 Linux 安装包：

```bash
npm run build
```

安装构建出的 deb（推荐，会装到 `/usr/bin/szu-network-guardian` 并写入应用菜单图标）：

```bash
sudo dpkg -i "src-tauri/target/release/bundle/deb/SZU Network Guardian_1.2.2_amd64.deb"
```

> [!IMPORTANT]
> 安装或移动程序后，请在界面里取消并重新勾选“开机自动启动”（或直接点“保存设置”），让自启动项更新为新的可执行文件路径。

GNOME（Ubuntu 默认桌面）需要 AppIndicator 扩展才能显示托盘图标，未安装时执行：

```bash
sudo apt install gnome-shell-extension-appindicator
```

开发模式会调用项目根目录的 `.venv/bin/python3`（不存在时使用 `python3`）运行认证桥接。Linux `.deb` 发行包会内置 `backend_api.py` 和 `szu_guardian`，运行时使用系统 `python3` 及 `python3-requests`。

### Android 移动端

Android 端是独立的 Kotlin 原生工程，认证协议与桌面端 Python 核心保持一致（见 [android/README.md](android/README.md)）：

```bash
cd android
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 Android Studio 打开仓库里的 `android` 目录运行。Android 上用前台服务常驻通知栏代替系统托盘，用 `BOOT_COMPLETED` 广播实现开机自启。

Ubuntu 24.04 如果尚未安装 Tk，先运行：

```bash
sudo apt install python3-tk
```

## 构建 Windows EXE

双击 `build.bat`，或在 PowerShell 中运行：

```powershell
.\build.ps1
```

构建脚本会创建独立的 `.venv-build` 环境、执行自动化测试，并生成：

```text
dist\SZU-Network-Guardian-v1.2.2.exe
```

也可以在仓库的 [Actions 页面](https://github.com/doublenuo/szu-network-guardian/actions/workflows/build-windows.yml) 手动运行构建流程，然后下载 Windows、Ubuntu 和 Android 构建产物。

> [!IMPORTANT]
> 请先把 EXE 移动到最终位置，再勾选“开机自动启动”。开机启动项会记录 EXE 的当前位置；移动文件后需要取消并重新勾选。

## 配置与日志

配置文件（Windows）：

```text
%LOCALAPPDATA%\SZUNetworkGuardian\config.json
```

Linux 默认配置文件：

```text
~/.config/SZUNetworkGuardian/config.json
```

Linux 默认完整日志目录：

```text
~/.config/SZUNetworkGuardian/logs
```

Windows 完整日志：

```text
%LOCALAPPDATA%\SZUNetworkGuardian\logs
```

日志使用 `guardian-YYYY-MM-DD.log` 命名。程序启动时和运行期间会自动清理 7 天前的日志。

## 与代理软件同时使用

程序默认勾选“检测和认证强制直连（推荐）”。开启后会：

1. 排除代理常用的 `198.18.0.0/15` Fake-IP 地址段。
2. 找到电脑真实的校园网有线或无线网卡。
3. 自动读取物理网卡通过 DHCP 获得的 DNS，并通过该网卡执行独立查询。
4. 将联网检测和校园网认证绑定到物理网卡，不经过系统代理或 TUN 虚拟网卡。

程序会优先使用与校园网 IPv4 地址属于同一物理网卡的 DNS。即使校园网调整 DNS 地址，也无需先连接外网或手动修改配置；当网卡未提供可用 DNS 时，程序才会依次尝试内置的校园网和公共 DNS 作为兜底。

正常日志会显示：

```text
网络连接正常（校园网直连）
```

教学/办公区每次检测还会先核验深澜认证会话。校园网注销后，即使百度首页等少数网页仍可访问，也会出现下面的日志并触发登录：

```text
检测到校园网认证已失效，正在重新认证…
正在使用教学 / 办公区认证…
```

如果直连模式提示找不到物理网卡，请先确认电脑仍连接校园有线网络或 `SZU_WLAN`。只有在未使用代理、且直连模式确实无法适配当前网络时，才建议取消勾选。

开机自启使用当前用户注册表项：

```text
HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run
```

Linux 开机自启使用：

```text
~/.config/autostart/SZUNetworkGuardian.desktop
```

## 认证接口

| 网络区域 | 认证方式 | 地址 |
| --- | --- | --- |
| 教学 / 办公区 | 深澜 SRun challenge 登录 | `https://net.szu.edu.cn` |
| 宿舍区 | ePortal 登录 | `http://172.30.255.42:801/eportal/portal/login/` |

教学区已经从旧版 Dr.COM 简单表单迁移到带 challenge 的 SRun 认证。相关实现集中在：

- `szu_guardian/srun.py`：SRun 加密和登录流程
- `szu_guardian/network.py`：区域识别、联网检测和重连流程
- `szu_guardian/monitor.py`：后台监控调度
- `szu_guardian/tray.py`：跨平台系统托盘

## 项目结构

```text
.
├── .github/workflows/       # Windows 自动构建
├── android/                 # Android 原生客户端（Kotlin + Compose）
├── szu_guardian/
│   ├── local_log.py         # 本地日志与自动清理
│   ├── direct_network.py    # 物理网卡绑定与直连 DNS
│   ├── monitor.py           # 后台监控
│   ├── network.py           # 网络检测与认证入口
│   ├── srun.py              # SRun 协议实现
│   ├── startup.py           # Windows 开机自启
│   ├── storage.py           # DPAPI 配置存储
│   ├── tray.py              # 系统托盘
│   └── ui.py                # 图形界面
├── tests/                   # 自动化测试
├── build.ps1                # Windows 打包脚本
├── main.py                  # 程序入口
└── requirements.txt
```

## 隐私与安全

- 源码和构建产物不包含预设账号或密码。
- 运行日志不会输出密码、认证 challenge 或完整认证载荷。
- Windows 密码通过 DPAPI 保存，通常只能由保存它的同一 Windows 用户解密；Linux 优先使用 Secret Service。
- 请勿把配置文件上传到公共仓库。Linux 配置文件位于 `~/.config/SZUNetworkGuardian/config.json`。

## Acknowledgements / 致谢

本项目在以下开源项目和公开技术资料的基础上完成，感谢原作者与贡献者：

- [ackness/szu-autoconnect](https://github.com/ackness/szu-autoconnect)：本项目最初参考的深圳大学校园网自动重连脚本，包括旧版 Dr.COM 登录思路和基础监控结构。
- [Sleepstars/SZU-login](https://github.com/Sleepstars/SZU-login)：提供新版深圳大学教学/办公区 SRun、宿舍区 ePortal 接口及登录流程的重要参考。
- [vidar-team/srun-login](https://github.com/vidar-team/srun-login)：SRun challenge、XXTEA/XEncode、校验和与自定义 Base64 流程的上游实现。
- E99p1ant 及上述项目的所有贡献者：感谢其在 SRun 协议实现与开源维护方面的工作。

`szu_guardian/srun.py` 包含基于 MIT 许可实现的 Python 移植。第三方归属和许可说明请参阅 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 贡献

欢迎提交 Issue 和 Pull Request。若学校调整认证方式，请附上脱敏后的响应格式或错误日志，切勿提交真实账号、密码或认证数据。

## 许可证

本项目采用 [MIT License](LICENSE)。

## 免责声明

本项目仅用于维护本人账号的正常校园网连接。请遵守学校网络管理规定；因认证接口变更、账号状态、网络环境或不当使用导致的问题，项目作者不承担责任。
