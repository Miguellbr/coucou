#if PHONE_LINK
import Foundation
import Network

@MainActor
final class AndroidRelayServer {
    static let shared = AndroidRelayServer()
    static let port: UInt16 = 8765
    private let queue = DispatchQueue(label: "coucou.android-relay")
    private var listener: NWListener?
    private(set) var token = ""

    func start() {
        guard listener == nil else { return }
        token = UserDefaults.standard.string(forKey: "androidRelayToken") ?? Self.makeToken()
        UserDefaults.standard.set(token, forKey: "androidRelayToken")
        do {
            let listener = try NWListener(using: .tcp, on: NWEndpoint.Port(rawValue: Self.port)!)
            listener.newConnectionHandler = { [weak self] connection in self?.handle(connection) }
            listener.stateUpdateHandler = { state in
                if case .failed(let error) = state { print("[AndroidRelay] \(error)") }
            }
            self.listener = listener
            listener.start(queue: queue)
            CloudProbe.shared.log("[android] relay listening on port \(Self.port), token \(token.prefix(8))…")
        } catch {
            CloudProbe.shared.log("[android] couldn't start relay: \(error.localizedDescription)")
        }
    }

    func stop() {
        listener?.cancel()
        listener = nil
    }

    private nonisolated func handle(_ connection: NWConnection) {
        connection.start(queue: DispatchQueue(label: "coucou.android.connection"))
        receive(connection, data: Data())
    }

    private nonisolated func receive(_ connection: NWConnection, data: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 128 * 1024) { [weak self] chunk, _, complete, error in
            var buffer = data
            if let chunk { buffer.append(chunk) }
            if let end = buffer.range(of: Data("\r
\r
".utf8)) {
                let headerText = String(decoding: buffer[..<end.lowerBound], as: UTF8.self)
                let length = Self.headerValue(headerText, "content-length").flatMap(Int.init) ?? 0
                let bodyStart = end.upperBound
                if buffer.count >= bodyStart + length {
                    self?.process(connection, request: headerText, body: Data(buffer[bodyStart..<(bodyStart + length)]))
                    return
                }
            }
            if !complete && error == nil { self?.receive(connection, data: buffer) } else { connection.cancel() }
        }
    }

    private nonisolated func process(_ connection: NWConnection, request: String, body: Data) {
        let parts = request.split(separator: "
").first?.split(separator: " ") ?? []
        guard parts.count >= 2 else { Self.respond(connection, 400, #"{"error":"bad request"}"#); return }
        let method = String(parts[0])
        let path = String(parts[1])
        let auth = Self.headerValue(request, "authorization")

        Task { @MainActor [weak self] in
            guard let self else { return }
            guard auth == "Bearer \(self.token)" else {
                Self.respond(connection, 401, #"{"error":"unauthorized"}"#); return
            }
            switch (method, path) {
            case ("GET", "/health"):
                Self.respond(connection, 200, #"{"ok":true,"name":"Coucou"}"#)
            case ("GET", "/sessions"):
                let map = SessionSnapshot.all(tasks: AppState.shared.tasks,
                                              approval: AppState.shared.pendingApproval,
                                              question: AppState.shared.pendingQuestion)
                guard let data = try? JSONEncoder().encode(Array(map.values)) else {
                    Self.respond(connection, 500, #"{"error":"encode failed"}"#); return
                }
                Self.respond(connection, 200, data: data)
            case ("POST", "/question"):
                guard let request = try? JSONDecoder().decode(AndroidQuestionRequest.self, from: body) else {
                    Self.respond(connection, 400, #"{"error":"invalid json"}"#); return
                }
                guard let pending = AppState.shared.pendingQuestion else {
                    Self.respond(connection, 409, #"{"accepted":false}"#); return
                }
                let payload = QuestionPayload(ask: pending)
                guard payload.fingerprint == request.fingerprint,
                      payload.accepts(request.selections) else {
                    Self.respond(connection, 409, #"{"accepted":false}"#); return
                }
                HookServer.shared.sendQuestionAnswers(
                    AskQuestion.buildAnswers(questions: pending.questions, selections: request.selections)
                )
                Self.respond(connection, 200, #"{"accepted":true}"#)

            case ("POST", "/approval"):
                guard let request = try? JSONDecoder().decode(AndroidApprovalRequest.self, from: body) else {
                    Self.respond(connection, 400, #"{"error":"invalid json"}"#); return
                }
                let accepted = ApprovalRelay.shared.submitAndroidDecision(request.decision, fingerprint: request.fingerprint)
                Self.respond(connection, accepted ? 200 : 409, accepted ? #"{"accepted":true}"# : #"{"accepted":false}"#)
            default:
                Self.respond(connection, 404, #"{"error":"not found"}"#)
            }
        }
    }

    private nonisolated static func headerValue(_ request: String, _ name: String) -> String? {
        request.split(separator: "
").dropFirst().compactMap { line in
            let p = line.split(separator: ":", maxSplits: 1).map(String.init)
            guard p.count == 2, p[0].trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == name else { return nil }
            return p[1].trimmingCharacters(in: .whitespacesAndNewlines)
        }.first
    }

    private nonisolated static func respond(_ connection: NWConnection, _ status: Int, _ json: String) {
        respond(connection, status, data: Data(json.utf8))
    }

    private nonisolated static func respond(_ connection: NWConnection, _ status: Int, data: Data) {
        let reason = status == 200 ? "OK" : status == 401 ? "Unauthorized" : status == 404 ? "Not Found" : status == 409 ? "Conflict" : "Bad Request"
        let head = "HTTP/1.1 \(status) \(reason)\r
Content-Type: application/json\r
Content-Length: \(data.count)\r
Connection: close\r
\r
"
        var response = Data(head.utf8); response.append(data)
        connection.send(content: response, completion: .contentProcessed { _ in connection.cancel() })
    }

    private nonisolated static func makeToken() -> String {
        (0..<32).map { _ in String(format: "%02x", UInt8.random(in: 0...255)) }.joined()
    }
}

struct AndroidQuestionRequest: Codable {
    let fingerprint: String
    let selections: [[String]]
}

struct AndroidInstructionRequest: Codable {
    let pillId: String
    let text: String
}

struct AndroidApprovalRequest: Codable {
    let fingerprint: String
    let decision: String
}
#endif