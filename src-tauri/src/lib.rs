use serde::de::DeserializeOwned;
use serde_json::{json, Value};
use std::sync::Mutex;
use std::{path::PathBuf, process::Command};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconEvent};
use tauri::{AppHandle, Emitter, Manager, State, WindowEvent};

struct Bridge(Mutex<()>);

/// 开机自启项传入的参数：静默启动，仅驻留托盘。
const AUTOSTART_ARG: &str = "--autostart";

fn python_path(root: &PathBuf) -> PathBuf {
    let local = root.join(".venv/bin/python3");
    if local.exists() {
        local
    } else {
        PathBuf::from("python3")
    }
}

/// 开机自启项需要指向真正打包出来的可执行文件。
/// AppImage 优先使用 .AppImage 文件本身，否则运行时会被写成临时挂载点里的路径。
fn packaged_executable() -> PathBuf {
    if let Ok(appimage) = std::env::var("APPIMAGE") {
        let path = PathBuf::from(appimage);
        if path.is_file() {
            return path;
        }
    }
    std::env::current_exe().unwrap_or_else(|_| PathBuf::from("szu-network-guardian"))
}

/// 把主窗口恢复到前台。
fn show_main_window(handle: &AppHandle) {
    if let Some(window) = handle.get_webview_window("main") {
        let _ = window.unminimize();
        let _ = window.show();
        let _ = window.set_focus();
    }
}

fn run_backend<T: DeserializeOwned>(app: &AppHandle, request: Value) -> Result<T, String> {
    let source_root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .unwrap()
        .to_path_buf();
    let resource_root = app.path().resource_dir().map_err(|e| e.to_string())?;
    let packaged_root = if resource_root.join("backend_api.py").exists() {
        Some(resource_root.clone())
    } else if resource_root.join("_up_").join("backend_api.py").exists() {
        Some(resource_root.join("_up_"))
    } else {
        None
    };
    let root = packaged_root.unwrap_or(source_root);
    let mut child = Command::new(python_path(&root))
        .arg(root.join("backend_api.py"))
        .env("SZU_GUARDIAN_EXECUTABLE", packaged_executable())
        .stdin(std::process::Stdio::piped())
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .spawn()
        .map_err(|e| e.to_string())?;
    use std::io::Write;
    child
        .stdin
        .take()
        .unwrap()
        .write_all(request.to_string().as_bytes())
        .map_err(|e| e.to_string())?;
    let result = child.wait_with_output().map_err(|e| e.to_string())?;
    if !result.status.success() {
        return Err(String::from_utf8_lossy(&result.stderr).to_string());
    }
    let value: Value =
        serde_json::from_slice(&result.stdout).map_err(|e| format!("后端返回格式错误：{e}"))?;
    if let Some(error) = value.get("error") {
        return Err(error.as_str().unwrap_or("后端错误").to_string());
    }
    serde_json::from_value(value).map_err(|e| e.to_string())
}

#[tauri::command]
fn load_config(app: AppHandle, state: State<'_, Bridge>) -> Result<Value, String> {
    let _guard = state.0.lock().unwrap();
    run_backend(&app, json!({"action":"load"}))
}
#[tauri::command]
fn save_config(app: AppHandle, state: State<'_, Bridge>, config: Value) -> Result<Value, String> {
    let _guard = state.0.lock().unwrap();
    run_backend(&app, json!({"action":"save", "config":config}))
}
#[tauri::command]
fn check_network(app: AppHandle, state: State<'_, Bridge>, config: Value) -> Result<Value, String> {
    let _guard = state.0.lock().unwrap();
    run_backend(&app, json!({"action":"check", "config":config}))
}
#[tauri::command]
fn open_logs(app: AppHandle, state: State<'_, Bridge>) -> Result<Value, String> {
    let _guard = state.0.lock().unwrap();
    run_backend(&app, json!({"action":"open_logs"}))
}
#[tauri::command]
fn set_autostart(app: AppHandle, state: State<'_, Bridge>, enabled: bool) -> Result<Value, String> {
    let _guard = state.0.lock().unwrap();
    run_backend(&app, json!({"action":"autostart", "enabled":enabled}))
}

#[tauri::command]
fn quit_app(app: AppHandle) {
    app.exit(0);
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .manage(Bridge(Mutex::new(())))
        .invoke_handler(tauri::generate_handler![
            load_config,
            save_config,
            check_network,
            open_logs,
            set_autostart,
            quit_app
        ])
        .setup(|app| {
            let menu = tauri::menu::MenuBuilder::new(app)
                .text("show", "打开主界面")
                .text("check", "立即检测")
                .separator()
                .text("quit", "退出程序")
                .build()?;
            let menu_handle = app.handle().clone();
            let click_handle = app.handle().clone();
            tauri::tray::TrayIconBuilder::with_id("main-tray")
                .menu(&menu)
                .tooltip("SZU Network Guardian")
                .on_menu_event(move |_tray, event| match event.id().0.as_str() {
                    "show" => show_main_window(&menu_handle),
                    "check" => {
                        let _ = menu_handle.emit("tray-check", ());
                    }
                    "quit" => menu_handle.exit(0),
                    _ => {}
                })
                .on_tray_icon_event(move |_tray, event| match event {
                    TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    }
                    | TrayIconEvent::DoubleClick {
                        button: MouseButton::Left,
                        ..
                    } => show_main_window(&click_handle),
                    _ => {}
                })
                .build(app)?;

            // 由开机自启项拉起时只驻留托盘，不弹出主窗口。
            if std::env::args().any(|arg| arg == AUTOSTART_ARG) {
                if let Some(window) = app.get_webview_window("main") {
                    let _ = window.hide();
                }
            }
            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                // 点击关闭只隐藏窗口，程序继续在托盘后台守护；
                // 真正退出请使用托盘菜单里的「退出程序」。
                let _ = window.hide();
                api.prevent_close();
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
