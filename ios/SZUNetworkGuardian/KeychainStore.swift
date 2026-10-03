import Foundation
import Security

final class KeychainStore {
    private let service = "com.weno.szuguardian.ios"

    func load() -> AppConfig {
        AppConfig(
            username: read("username") ?? "",
            password: read("password") ?? "",
            zone: Zone(rawValue: read("zone") ?? "office") ?? .office,
            intervalMinutes: max(1, min(1440, Int(read("interval") ?? "15") ?? 15))
        )
    }

    func save(_ config: AppConfig) {
        write(config.username.trimmingCharacters(in: .whitespacesAndNewlines), key: "username")
        write(config.password, key: "password")
        write(config.zone.rawValue, key: "zone")
        write(String(config.intervalMinutes), key: "interval")
    }

    private func read(_ key: String) -> String? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service, kSecAttrAccount as String: key,
            kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func write(_ value: String, key: String) {
        let data = Data(value.utf8)
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service, kSecAttrAccount as String: key]
        SecItemDelete(query as CFDictionary)
        SecItemAdd(query.merging([kSecValueData as String: data]) { $1 } as CFDictionary, nil)
    }
}
