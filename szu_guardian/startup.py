from __future__ import annotations

import os
import subprocess
import sys
import shlex
from pathlib import Path


RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
VALUE_NAME = "SZUNetworkGuardian"
LINUX_AUTOSTART_PATH = Path.home() / ".config" / "autostart" / f"{VALUE_NAME}.desktop"
# 打包后的桌面端（Tauri）会把自身可执行文件路径注入该环境变量，
# AppImage 场景下传入的是 .AppImage 文件本身，而不是临时挂载点里的二进制。
EXECUTABLE_ENV = "SZU_GUARDIAN_EXECUTABLE"
AUTOSTART_ARG = "--autostart"


def _startup_command() -> str:
    if getattr(sys, "frozen", False):
        parts = [sys.executable, AUTOSTART_ARG]
    else:
        python = Path(sys.executable)
        pythonw = python.with_name("pythonw.exe")
        executable = pythonw if pythonw.exists() else python
        main_file = Path(__file__).resolve().parents[1] / "main.py"
        parts = [str(executable), str(main_file), AUTOSTART_ARG]
    return subprocess.list2cmdline(parts)


def _linux_startup_command() -> str:
    """Linux 下写入自启动项的 Exec 命令。

    桌面端（Tauri 打包）通过 ``SZU_GUARDIAN_EXECUTABLE`` 传入自身路径；
    源码运行时退回 ``python main.py --autostart``。
    """
    packaged = os.environ.get(EXECUTABLE_ENV, "").strip()
    if packaged:
        return shlex.join([packaged, AUTOSTART_ARG])
    main_file = Path(__file__).resolve().parents[1] / "main.py"
    return shlex.join([sys.executable, str(main_file), AUTOSTART_ARG])


def _linux_desktop_entry() -> str:
    return (
        "[Desktop Entry]\n"
        "Type=Application\n"
        "Version=1.0\n"
        "Name=SZU 网络守护\n"
        "Comment=深圳大学校园网自动重连\n"
        f"Exec={_linux_startup_command()}\n"
        "Icon=szu-network-guardian\n"
        "Terminal=false\n"
        # 仅作为自启动项存在，不在应用菜单里重复显示。
        "NoDisplay=true\n"
        "StartupNotify=false\n"
        "Categories=Network;\n"
        "X-GNOME-Autostart-enabled=true\n"
        # 等会话（网络、密钥环）就绪后再启动，避免开机瞬间检测失败。
        "X-GNOME-Autostart-Delay=10\n"
    )


def _linux_write_autostart() -> None:
    LINUX_AUTOSTART_PATH.parent.mkdir(parents=True, exist_ok=True)
    LINUX_AUTOSTART_PATH.write_text(_linux_desktop_entry(), encoding="utf-8")
    try:
        LINUX_AUTOSTART_PATH.chmod(0o644)
    except OSError:
        pass


def _linux_autostart_enabled() -> bool:
    """存在自启动文件且未被桌面环境显式禁用才算开启。"""
    if not LINUX_AUTOSTART_PATH.exists():
        return False
    try:
        content = LINUX_AUTOSTART_PATH.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return False
    for line in content.splitlines():
        if line.strip().lower().replace(" ", "") == "x-gnome-autostart-enabled=false":
            return False
    return True


def is_autostart_enabled() -> bool:
    if sys.platform != "win32":
        return _linux_autostart_enabled() if sys.platform.startswith("linux") else False
    import winreg

    try:
        with winreg.OpenKey(
            winreg.HKEY_CURRENT_USER,
            RUN_KEY,
            0,
            winreg.KEY_READ,
        ) as key:
            value, _ = winreg.QueryValueEx(key, VALUE_NAME)
            return bool(value)
    except FileNotFoundError:
        return False


def set_autostart(enabled: bool) -> None:
    if sys.platform != "win32":
        if not sys.platform.startswith("linux"):
            if enabled:
                raise OSError("当前系统暂不支持开机自启")
            return
        if enabled:
            _linux_write_autostart()
        else:
            try:
                LINUX_AUTOSTART_PATH.unlink()
            except FileNotFoundError:
                pass
        return
    import winreg

    with winreg.CreateKeyEx(
        winreg.HKEY_CURRENT_USER,
        RUN_KEY,
        0,
        winreg.KEY_SET_VALUE,
    ) as key:
        if enabled:
            winreg.SetValueEx(
                key,
                VALUE_NAME,
                0,
                winreg.REG_SZ,
                _startup_command(),
            )
        else:
            try:
                winreg.DeleteValue(key, VALUE_NAME)
            except FileNotFoundError:
                pass
