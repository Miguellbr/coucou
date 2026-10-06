#if PHONE_LINK
import Foundation
import Network

@MainActor
final class AndroidDiscoveryResponder {
    static let shared = AndroidDiscoveryResponder()
    private let queue = DispatchQueue(label: "coucou.android-discovery")
    private var listener: NWListener?

    func start() {
        guard listener == nil else { return }
        do {
            let listener = try NWListener(using: .udp, on: NWEndpoint.Port(rawValue: 8766)!)
            listener.newConnectionHandler = { [weak self] connection in self?.handle(connection) }
            listener.start(queue: queue)
            self.listener = listener
        } catch {
            CloudProbe.shared.log("[android] discovery failed: \(error.localizedDescription)")
        }
    }

    func stop() {
        listener?.cancel()
        listener = nil
    }

    private nonisolated func handle(_ connection: NWConnection) {
        connection.start(queue: DispatchQueue(label: "coucou.android.discovery.connection"))
        connection.receiveMessage { [weak self] data, _, _, _ in
            guard let data, String(data: data, encoding: .utf8) == "COUCOU_DISCOVER" else {
                connection.cancel()
                return
            }
            Task { @MainActor in
                let token = AndroidRelayServer.shared.token
                let response = "COUCOU_RELAY|\(AndroidRelayServer.port)|\(token)"
                connection.send(content: Data(response.utf8), completion: .contentProcessed { _ in connection.cancel() })
                _ = self
            }
        }
    }
}
#endif
