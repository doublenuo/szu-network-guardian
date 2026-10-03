import { invoke } from "@tauri-apps/api/core";

export type Config = { username: string; password: string; zone: "auto" | "office" | "dormitory"; interval_minutes: number; autostart: boolean; start_on_launch: boolean };
export type Result = { connected: boolean; message: string; latency_ms?: number | null };

export const api = {
  load: () => invoke<Config>("load_config"),
  save: (config: Config) => invoke<void>("save_config", { config }),
  check: (config: Config) => invoke<Result>("check_network", { config }),
  openLogs: () => invoke<void>("open_logs"),
  setAutostart: (enabled: boolean) => invoke<void>("set_autostart", { enabled }),
  quit: () => invoke<void>("quit_app"),
};
