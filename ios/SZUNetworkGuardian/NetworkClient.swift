import Foundation

struct ConnectionResult { let connected: Bool; let message: String; let latencyMs: Int? }

private final class TrustAllDelegate: NSObject, URLSessionDelegate {
    func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge,
                    completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        if let trust = challenge.protectionSpace.serverTrust {
            completionHandler(.useCredential, URLCredential(trust: trust))
        } else { completionHandler(.performDefaultHandling, nil) }
    }
}

final class NetworkClient {
    private let session: URLSession
    private let srun = SrunClient()
    private let dormitoryURL = "http://172.30.255.42:801/eportal/portal/login/"

    init() { session = URLSession(configuration: .ephemeral, delegate: TrustAllDelegate(), delegateQueue: nil) }

    func ensureConnected(_ config: AppConfig, progress: @escaping (String) -> Void) async -> ConnectionResult {
        let current = await checkConnection()
        if current.connected { return current }
        do {
            progress("正在认证校园网…")
            switch config.zone {
            case .office: _ = try await srun.login(session: session, username: config.username, password: config.password)
            case .dormitory: _ = try await loginDormitory(config, session: session)
            case .auto:
                do { _ = try await srun.login(session: session, username: config.username, password: config.password) }
                catch { _ = try await loginDormitory(config, session: session) }
            }
            try await Task.sleep(for: .seconds(2))
            let verified = await checkConnection()
            return verified.connected ? ConnectionResult(true, "已自动重连，网络恢复正常", verified.latencyMs)
                : ConnectionResult(false, "认证已完成，但外网仍不可用", nil)
        } catch { return ConnectionResult(false, error.localizedDescription, nil) }
    }

    func checkConnection() async -> ConnectionResult {
        let started = Date()
        for (urlString, expected) in [("https://www.baidu.com/favicon.ico", "baidu.com"),
                                       ("http://www.msftconnecttest.com/connecttest.txt", "Microsoft Connect Test")] {
            guard let url = URL(string: urlString) else { continue }
            do {
                let (data, response) = try await session.data(from: url)
                let http = response as? HTTPURLResponse
                let valid = http?.statusCode == 200 && (expected == "baidu.com"
                    ? (http?.url?.host?.hasSuffix("baidu.com") == true)
                    : String(data: data, encoding: .utf8)?.contains(expected) == true)
                if valid { return ConnectionResult(true, "网络连接正常", Int(Date().timeIntervalSince(started) * 1000)) }
            } catch { continue }
        }
        return ConnectionResult(false, "直连外网不可用", nil)
    }

    private func loginDormitory(_ config: AppConfig, session: URLSession) async throws -> String {
        var components = URLComponents(string: dormitoryURL)!
        components.queryItems = [URLQueryItem(name: "user_account", value: config.username),
                                 URLQueryItem(name: "user_password", value: config.password)]
        let (data, response) = try await session.data(from: components.url!)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw GuardianError.message("宿舍区认证服务器请求失败") }
        let text = String(data: data, encoding: .utf8) ?? ""
        guard let start = text.firstIndex(of: "("), let end = text.lastIndex(of: ")") else { throw GuardianError.message("宿舍区认证响应格式错误") }
        let object = try JSONSerialization.jsonObject(with: Data(text[text.index(after: start)..<end].utf8)) as? [String: Any]
        let message = object?["msg"] as? String ?? ""
        let result = String(describing: object?["result"] ?? "")
        guard result == "1" || result == "true" || message.contains("成功") || message.lowercased().contains("success") else {
            throw GuardianError.message(message.isEmpty ? "宿舍区认证失败" : message)
        }
        return message
    }
}

enum GuardianError: LocalizedError { case message(String); var errorDescription: String? { if case .message(let value) = self { value } else { nil } } }
