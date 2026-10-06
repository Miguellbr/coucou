package com.miguellbr.coucou.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class RelayCandidate(val host: String, val port: Int, val token: String)

class RelayDiscovery {
    suspend fun discover(timeoutMs: Int = 1200): RelayCandidate? = withContext(Dispatchers.IO) {
        val socket = DatagramSocket()
        try {
            socket.broadcast = true
            socket.soTimeout = timeoutMs
            val message = "COUCOU_DISCOVER".toByteArray()
            val packet = DatagramPacket(message, message.size, InetAddress.getByName("255.255.255.255"), 8766)
            socket.send(packet)

            val buffer = ByteArray(2048)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val text = String(response.data, 0, response.length)
            val parts = text.split("|", limit = 3)
            if (parts.size == 3 && parts[0] == "COUCOU_RELAY") {
                RelayCandidate(response.address.hostAddress ?: return@withContext null, parts[1].toIntOrNull() ?: 8765, parts[2])
            } else null
        } finally {
            socket.close()
        }
    }
}
