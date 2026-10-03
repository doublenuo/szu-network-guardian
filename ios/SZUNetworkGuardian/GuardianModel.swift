import Foundation
import Combine
import UserNotifications

@MainActor final class GuardianModel: ObservableObject {
    @Published var config: AppConfig
    @Published var status = ConnectionStatus()
    @Published var isBusy = false
    @Published var log: [String] = []
    private let store = KeychainStore()
    private let client = NetworkClient()

    init() { config = store.load() }

    func saveAndCheck() {
        guard !config.username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { status = ConnectionStatus(phase: .offline, message: "请输入校园网账号", latencyMs: nil); return }
        guard !config.password.isEmpty else { status = ConnectionStatus(phase: .offline, message: "请输入校园网密码", latencyMs: nil); return }
        store.save(config); check()
    }

    func check() {
        guard !isBusy else { return }
        isBusy = true; status = ConnectionStatus(phase: .checking, message: "正在检测网络…", latencyMs: nil)
        Task {
            let result = await client.ensureConnected(config) { [weak self] message in
                Task { @MainActor in self?.status = ConnectionStatus(phase: .checking, message: message, latencyMs: nil) }
            }
            status = ConnectionStatus(phase: result.connected ? .online : .offline, message: result.message, latencyMs: result.latencyMs)
            log.insert("\(Date.formatted(date: .omitted, time: .shortened))  \(result.message)", at: 0); log = Array(log.prefix(30)); isBusy = false
            if !result.connected { notify(result.message) }
            BackgroundScheduler.schedule(after: config.intervalMinutes)
        }
    }

    private func notify(_ message: String) {
        let content = UNMutableNotificationContent(); content.title = "SZU 网络守护"; content.body = message; content.sound = .default
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil))
    }
}
