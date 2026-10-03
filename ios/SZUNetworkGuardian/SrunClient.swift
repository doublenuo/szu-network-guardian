import Foundation
import CommonCrypto

struct SrunLoginResult { let success: Bool; let message: String }

final class SrunClient {
    private let base = "https://net.szu.edu.cn"
    private let alpha = Array("LVoJPiCN2R8G90yg+hmFHuacZ1OWMnrsSTXkYpUq/3dlbfKwv6xztjI7DeBE45QA")
    private let standard = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")

    func login(session: URLSession, username: String, password: String) async throws -> SrunLoginResult {
        let acId = try await get(session, path: "/").flatMap { String(data: $0, encoding: .utf8) }.flatMap { $0.range(of: "ac_id=([0-9]+)", options: .regularExpression).map { String($0) } }?.split(separator: "=").last.map(String.init) ?? "1"
        let challenge = try await jsonp(session, path: "/cgi-bin/get_challenge", params: ["callback":"_", "username":username, "ip":""])
        let token = challenge["challenge"] as? String ?? ""
        let ip = challenge["client_ip"] as? String ?? ""
        guard !token.isEmpty, !ip.isEmpty else { throw GuardianError.message("认证服务器未返回 challenge 或客户端 IP") }
        let passwordMd5 = hmacMD5(password, key: token)
        let infoJSON: [String: Any] = ["username": username, "password": password, "ip": ip, "acid": acId, "enc_ver": "srun_bx1"]
        let infoData = try JSONSerialization.data(withJSONObject: infoJSON, options: [.withoutEscapingSlashes])
        let info = "{SRBX1}" + customBase64(xencode(Data(infoData), key: Data(token.utf8)))
        let checksum = sha1((token + username + token + passwordMd5 + token + acId + token + ip + token + "200" + token + "1" + token + info))
        let response = try await jsonp(session, path: "/cgi-bin/srun_portal", params: ["callback":"_", "action":"login", "username":username, "password":"{MD5}" + passwordMd5, "os":"iOS", "name":"iOS", "double_stack":"0", "info":info, "chksum":checksum, "ac_id":acId, "ip":ip, "n":"200", "type":"1"])
        let success = response["error"] as? String == "ok" || response["res"] as? String == "ok" || String(describing: response["st"] ?? "") == "1"
        return SrunLoginResult(success: success, message: success ? "教学 / 办公区认证成功" : (response["error_msg"] as? String ?? "认证失败"))
    }

    private func get(_ session: URLSession, path: String) async throws -> Data { let (data, response) = try await session.data(from: URL(string: base + path)!); guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw GuardianError.message("认证服务器请求失败") }; return data }
    private func jsonp(_ session: URLSession, path: String, params: [String: String]) async throws -> [String: Any] {
        var c = URLComponents(string: base + path)!; c.queryItems = params.map { URLQueryItem(name: $0.key, value: $0.value) }
        let data = try await get(session, path: c.url!.absoluteString.replacingOccurrences(of: base, with: "")); let text = String(data: data, encoding: .utf8) ?? ""
        guard let start = text.firstIndex(of: "("), let end = text.lastIndex(of: ")") else { throw GuardianError.message("认证服务器响应格式错误") }
        return try JSONSerialization.jsonObject(with: Data(text[text.index(after: start)..<end].utf8)) as? [String: Any] ?? [:]
    }

    private func hmacMD5(_ value: String, key: String) -> String { MD5.hmac(value, key: key) }
    private func sha1(_ value: String) -> String { SHA1.hash(value) }

    private func xencode(_ content: Data, key: Data) -> Data {
        var values = words(content, length: true)
        var keys = words(key, length: false)
        keys += Array(repeating: 0, count: max(0, 4 - keys.count))
        let n = values.count - 1
        var z = values[n]
        var total: UInt32 = 0
        let delta: UInt32 = 0x9E3779B9
        for _ in 0..<(6 + 52 / (n + 1)) {
            total &+= delta
            let e = Int((total >> 2) & 3)
            for p in 0..<n {
                let y = values[p + 1]
                var mixed = (z >> 5) ^ (y << 2)
                mixed &+= (y >> 3) ^ (z << 4)
                mixed ^= total ^ y
                mixed &+= keys[(p & 3) ^ e] ^ z
                values[p] &+= mixed
                z = values[p]
            }
            let y = values[0]
            var mixed = (z >> 5) ^ (y << 2)
            mixed &+= (y >> 3) ^ (z << 4)
            mixed ^= total ^ y
            mixed &+= keys[(n & 3) ^ e] ^ z
            values[n] &+= mixed
            z = values[n]
        }
        var output = Data()
        values.forEach { value in
            output.append(UInt8(value & 0xFF)); output.append(UInt8((value >> 8) & 0xFF))
            output.append(UInt8((value >> 16) & 0xFF)); output.append(UInt8((value >> 24) & 0xFF))
        }
        return output
    }
    private func words(_ data: Data, length: Bool) -> [UInt32] { var out = [UInt32](repeating: 0, count: (data.count + 3) / 4 + (length ? 1 : 0)); for i in stride(from: 0, to: data.count, by: 4) { for j in 0..<4 where i + j < data.count { out[i / 4] |= UInt32(data[i + j]) << UInt32(j * 8) } }; if length { out[out.count - 1] = UInt32(data.count) }; return out }
    private func customBase64(_ data: Data) -> String { Data(data).base64EncodedString().map { c in guard let i = standard.firstIndex(of: c) else { return c }; return alpha[i] }.reduce(into: "") { $0.append($1) } }
}

enum MD5 { static func hmac(_ value: String, key: String) -> String { HMAC.digest(value, key: key, algorithm: .md5) } }
enum SHA1 {
    static func hash(_ value: String) -> String {
        let data = Data(value.utf8)
        var result = [UInt8](repeating: 0, count: Int(CC_SHA1_DIGEST_LENGTH))
        data.withUnsafeBytes { CC_SHA1($0.baseAddress, CC_LONG(data.count), &result) }
        return result.map { String(format: "%02x", $0) }.joined()
    }
}
enum HMAC {
    enum Algorithm { case md5, sha1 }
    static func digest(_ value: String, key: String, algorithm: Algorithm) -> String {
        let keyData = Data(key.utf8), valueData = Data(value.utf8)
        var result = [UInt8](repeating: 0, count: algorithm == .md5 ? Int(CC_MD5_DIGEST_LENGTH) : Int(CC_SHA1_DIGEST_LENGTH))
        result.withUnsafeMutableBytes { output in
            keyData.withUnsafeBytes { keyBuffer in
                valueData.withUnsafeBytes { valueBuffer in
                    let algorithmId = algorithm == .md5 ? CCHmacAlgorithm(kCCHmacAlgMD5) : CCHmacAlgorithm(kCCHmacAlgSHA1)
                    CCHmac(algorithmId, keyBuffer.baseAddress, keyData.count, valueBuffer.baseAddress, valueData.count, output.baseAddress)
                }
            }
        }
        return result.map { String(format: "%02x", $0) }.joined()
    }
}
