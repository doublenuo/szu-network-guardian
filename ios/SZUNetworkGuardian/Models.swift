import Foundation

enum Zone: String, CaseIterable, Identifiable {
    case office, dormitory, auto
    var id: String { rawValue }
    var title: String {
        switch self { case .office: "教学 / 办公区"; case .dormitory: "宿舍区"; case .auto: "自动" }
    }
}

struct AppConfig {
    var username = ""
    var password = ""
    var zone: Zone = .office
    var intervalMinutes = 15
}

enum ConnectionPhase { case idle, checking, online, offline }

struct ConnectionStatus {
    var phase: ConnectionPhase = .idle
    var message = "尚未检测"
    var latencyMs: Int?
}
