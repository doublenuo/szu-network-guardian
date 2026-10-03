"""Small JSON stdin/stdout bridge used by the Tauri desktop shell."""
from __future__ import annotations
import json, sys
from szu_guardian.local_log import LocalLog, default_log_directory
from szu_guardian.models import AppConfig
from szu_guardian.network import NetworkClient
from szu_guardian.startup import set_autostart
from szu_guardian.storage import ConfigStore

def main() -> int:
    request = json.load(sys.stdin); action = request.get("action")
    store = ConfigStore()
    if action == "load":
        config = store.load(); print(json.dumps(config.__dict__ if hasattr(config, "__dict__") else {"username": config.username, "password": config.password, "zone": config.zone, "interval_minutes": config.interval_minutes, "autostart": config.autostart, "start_on_launch": config.start_on_launch}, ensure_ascii=False)); return 0
    if action == "save":
        data = request["config"]; config = AppConfig(**data); config.validate(); store.save(config); set_autostart(config.autostart); print("{}"); return 0
    if action == "check":
        data = request["config"]; config = AppConfig(**data); config.validate(); result = NetworkClient().ensure_connected(config); LocalLog().write(result.message, "success" if result.connected else "warning"); print(json.dumps({"connected": result.connected, "message": result.message, "latency_ms": result.latency_ms}, ensure_ascii=False)); return 0
    if action == "open_logs":
        LocalLog().open_directory(); print("{}"); return 0
    if action == "autostart":
        set_autostart(bool(request["enabled"])); print("{}"); return 0
    raise ValueError(f"unknown action: {action}")

if __name__ == "__main__":
    try: main()
    except Exception as exc:
        print(json.dumps({"error": str(exc)}, ensure_ascii=False)); raise
