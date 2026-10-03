import { useEffect, useRef, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { Activity, CheckCircle2, Eye, EyeOff, FolderOpen, LockKeyhole, Play, RefreshCw, Save, ShieldCheck, Square, WifiOff } from "lucide-react";
import { api, Config, Result } from "./api";

const initial: Config = { username: "", password: "", zone: "office", interval_minutes: 1, autostart: false, start_on_launch: true };
type State = "idle" | "checking" | "online" | "offline" | "waiting";

export default function App() {
  const [config, setConfig] = useState<Config>(initial); const [state, setState] = useState<State>("idle");
  const [loaded, setLoaded] = useState(false);
  const [detail, setDetail] = useState("尚未启动监控"); const [logs, setLogs] = useState<string[]>([]);
  const [showPassword, setShowPassword] = useState(false); const [running, setRunning] = useState(false); const [busy, setBusy] = useState(false);
  const runningRef = useRef(false);
  const timer = useRef<number | undefined>(undefined);
  const addLog = (message: string) => setLogs((old) => [...old.slice(-80), `${new Date().toLocaleTimeString()}  ${message}`]);

  useEffect(() => { api.load().then((value) => { setConfig(value); setLoaded(true); addLog("配置已加载"); }).catch((e) => addLog(`加载配置失败：${e}`)); return () => window.clearTimeout(timer.current); }, []);
  const update = <K extends keyof Config>(key: K, value: Config[K]) => setConfig((old) => ({ ...old, [key]: value }));
  const save = async (): Promise<boolean> => { if (!config.username.trim() || !config.password) { addLog("请先填写账号和密码"); return false; } if (config.interval_minutes < 1) { addLog("检测间隔必须大于 0"); return false; } setBusy(true); try { await api.save(config); await api.setAutostart(config.autostart); addLog("设置已保存"); return true; } catch (e) { addLog(`保存失败：${e}`); return false; } finally { setBusy(false); } };
  const check = async () => { setState("checking"); setDetail("正在检测网络状态…"); try { const result: Result = await api.check(config); if (result.connected) { setState("online"); setDetail(result.latency_ms ? `${result.latency_ms} ms` : result.message); addLog(result.message); } else { setState("offline"); setDetail(result.message); addLog(result.message); } } catch (e) { setState("offline"); setDetail(String(e)); addLog(`检测失败：${e}`); } };
  const start = async () => { if (!(await save())) return; runningRef.current = true; setRunning(true); await check(); addLog(`开始监控，间隔 ${config.interval_minutes} 分钟`); const loop = () => { if (!runningRef.current) return; void check(); timer.current = window.setTimeout(loop, config.interval_minutes * 60_000); }; timer.current = window.setTimeout(loop, config.interval_minutes * 60_000); };
  const stop = () => { window.clearTimeout(timer.current); runningRef.current = false; setRunning(false); setState("idle"); setDetail("监控已停止"); addLog("监控已停止"); };
  useEffect(() => { if (loaded && config.start_on_launch && config.username && config.password) void start(); }, [loaded]);
  useEffect(() => { let unlisten: (() => void) | undefined; void listen("tray-check", () => void check()).then((fn) => { unlisten = fn; }); return () => unlisten?.(); }, []);
  const color = { idle: "#94a3b8", checking: "#2563eb", online: "#16a34a", waiting: "#16a34a", offline: "#dc2626" }[state];
  return <main className="shell"><header><div className="brand"><div className="brand-mark"><ShieldCheck size={25}/></div><div><h1>SZU 网络守护</h1><p>轻量、安静地守护你的校园网连接</p></div></div><span className={`pill ${state}`}><i style={{ background: color }}/>{state === "online" ? "网络正常" : state === "checking" ? "检测中" : state === "offline" ? "网络异常" : state === "waiting" ? "守护中" : "等待开始"}</span></header>
    <section className="status-card"><div className="status-icon" style={{ color }}>{state === "online" ? <CheckCircle2/> : state === "offline" ? <WifiOff/> : <Activity/>}</div><div><strong>{detail}</strong><small>{running ? `每 ${config.interval_minutes} 分钟自动检测` : "尚未启动监控"}</small></div></section>
    <section className="card"><div className="card-title"><LockKeyhole size={18}/>连接设置</div><div className="form-grid"><label>账号<input value={config.username} onChange={(e) => update("username", e.target.value)} placeholder="校园网账号" /></label><label>密码<div className="input-wrap"><input type={showPassword ? "text" : "password"} value={config.password} onChange={(e) => update("password", e.target.value)} placeholder="统一身份认证密码"/><button className="icon-button" onClick={() => setShowPassword(!showPassword)}>{showPassword ? <EyeOff size={17}/> : <Eye size={17}/>}</button></div></label><label>网络区域<select value={config.zone} onChange={(e) => update("zone", e.target.value as Config["zone"])}><option value="office">教学 / 办公区（实验室）</option><option value="dormitory">宿舍区</option><option value="auto">自动尝试（实验性）</option></select></label><label>检测间隔<div className="number-wrap"><input type="number" min="1" max="1440" value={config.interval_minutes} onChange={(e) => update("interval_minutes", Number(e.target.value))}/><span>分钟</span></div></label></div><div className="checks"><label><input type="checkbox" checked={config.autostart} onChange={(e) => update("autostart", e.target.checked)}/>开机自动启动</label><label><input type="checkbox" checked={config.start_on_launch} onChange={(e) => update("start_on_launch", e.target.checked)}/>程序启动后自动监控</label></div></section>
    <div className="actions"><button className="primary" disabled={busy || running} onClick={() => void start()}><Play size={17}/>开始守护</button><button className="secondary" disabled={busy} onClick={() => void check()}><RefreshCw size={17}/>立即检测</button><button className="secondary" disabled={!running} onClick={stop}><Square size={16}/>停止</button></div>
    <section className="card logs"><div className="card-title"><Activity size={18}/>运行记录 <span>最近 3 小时</span><div className="log-actions"><button onClick={() => void api.openLogs()}><FolderOpen size={15}/>日志目录</button><button onClick={() => void save()}><Save size={15}/>保存设置</button><button onClick={() => void api.quit()}>退出程序</button></div></div><div className="log-list">{logs.length ? logs.map((log, i) => <div key={i}>{log}</div>) : <em>暂无运行记录</em>}</div></section><footer>关闭窗口仅最小化到托盘，后台继续守护 · 退出请使用「退出程序」或托盘菜单</footer>
  </main>;
}
