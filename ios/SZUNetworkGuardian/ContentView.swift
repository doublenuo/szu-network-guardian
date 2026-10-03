import SwiftUI
import UserNotifications

struct ContentView: View {
    @StateObject private var model = GuardianModel()

    var body: some View {
        NavigationStack {
            Form {
                Section("当前状态") {
                    Label {
                        VStack(alignment: .leading) {
                            Text(model.status.message).font(.headline)
                            if let latency = model.status.latencyMs { Text("延迟 \(latency) ms").font(.caption).foregroundStyle(.secondary) }
                        }
                    } icon: { Circle().fill(color).frame(width: 12, height: 12) }
                }
                Section("连接设置") {
                    TextField("校园网账号", text: $model.config.username).textInputAutocapitalization(.never).autocorrectionDisabled()
                    SecureField("统一身份认证密码", text: $model.config.password)
                    Picker("网络区域", selection: $model.config.zone) { ForEach(Zone.allCases) { Text($0.title).tag($0) } }
                    Stepper("后台检测间隔：\(model.config.intervalMinutes) 分钟", value: $model.config.intervalMinutes, in: 1...1440)
                }
                Section {
                    Button(model.isBusy ? "检测中…" : "保存并检测") { model.saveAndCheck() }.disabled(model.isBusy)
                    Button("立即检测") { model.check() }.disabled(model.isBusy)
                }
                Section("运行记录") { if model.log.isEmpty { Text("暂无运行记录").foregroundStyle(.secondary) } else { ForEach(model.log, id: \.self) { Text($0).font(.caption) } } }
                Section { Text("iOS 后台刷新由系统调度，无法像 Android 前台服务一样保证持续运行。建议开启通知和后台 App 刷新。").font(.footnote).foregroundStyle(.secondary) }
            }
            .navigationTitle("SZU 网络守护")
            .task { try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]); BackgroundScheduler.schedule(after: model.config.intervalMinutes) }
        }
    }

    private var color: Color { switch model.status.phase { case .online: .green; case .checking: .blue; case .offline: .red; case .idle: .gray } }
}
