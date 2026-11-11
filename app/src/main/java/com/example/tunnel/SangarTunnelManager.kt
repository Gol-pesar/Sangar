package com.example.tunnel

import com.example.crypto.SangarCrypto
import com.example.data.LogManager
import com.example.model.ProxyProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class SangarTunnelManager(
    private val profile: ProxyProfile,
    private val scope: CoroutineScope
) {
    private val activeConnectionsCount = AtomicInteger(0)
    private val totalUploadBytes = AtomicLong(0)
    private val totalDownloadBytes = AtomicLong(0)

    private val _connectionsFlow = MutableStateFlow(0)
    val connectionsFlow: StateFlow<Int> = _connectionsFlow.asStateFlow()

    private val _uploadBytesFlow = MutableStateFlow(0L)
    val uploadBytesFlow: StateFlow<Long> = _uploadBytesFlow.asStateFlow()

    private val _downloadBytesFlow = MutableStateFlow(0L)
    val downloadBytesFlow: StateFlow<Long> = _downloadBytesFlow.asStateFlow()

    private val okHttpClient: OkHttpClient by lazy {
        val workerHost = profile.getCleanWorkerHost()
        val cleanIp = profile.cleanIp.trim()

        val customDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                if (cleanIp.isNotEmpty() && hostname.equals(workerHost, ignoreCase = true)) {
                    try {
                        return listOf(InetAddress.getByName(cleanIp))
                    } catch (e: Exception) {
                        LogManager.warn("Clean IP $cleanIp failed to resolve, falling back to system DNS: ${e.message}")
                    }
                }
                return Dns.SYSTEM.lookup(hostname)
            }
        }

        OkHttpClient.Builder()
            .dns(customDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for websockets
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun handleConnection(clientSocket: Socket, targetHost: String, targetPort: Int) {
        val connectionId = activeConnectionsCount.incrementAndGet()
        _connectionsFlow.value = connectionId
        LogManager.info("[$connectionId] New SOCKS5 request -> $targetHost:$targetPort")

        val (initPacket, key) = SangarCrypto.buildInit(profile.uuid, targetHost, targetPort)
        val workerHost = profile.getCleanWorkerHost()
        val wsUrl = "wss://$workerHost/"

        val handshakeDeferred = CompletableDeferred<Boolean>()
        var activeWebSocket: WebSocket? = null

        val clientIn: InputStream = clientSocket.getInputStream()
        val clientOut: OutputStream = clientSocket.getOutputStream()

        val request = Request.Builder()
            .url(wsUrl)
            .header("Host", workerHost)
            .header("Sec-WebSocket-Protocol", "chat.v2")
            .build()

        var ackVerified = false

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                activeWebSocket = webSocket
                LogManager.debug("[$connectionId] WS Connected to $workerHost, sending init (${initPacket.size} bytes)")
                webSocket.send(initPacket.toByteString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                if (!ackVerified) {
                    if (SangarCrypto.verifyAck(data, key)) {
                        ackVerified = true
                        LogManager.success("[$connectionId] WS Ack verified! Tunnel active -> $targetHost:$targetPort")
                        handshakeDeferred.complete(true)
                    } else {
                        LogManager.error("[$connectionId] WS Ack mismatch or invalid key")
                        handshakeDeferred.complete(false)
                        webSocket.close(1000, "Ack verification failed")
                    }
                    return
                }

                // Normal downstream data: decrypt and write to client socket
                try {
                    val decrypted = SangarCrypto.xorBuffer(data, key, data.size)
                    synchronized(clientOut) {
                        clientOut.write(decrypted)
                        clientOut.flush()
                    }
                    val newTotal = totalDownloadBytes.addAndGet(data.size.toLong())
                    _downloadBytesFlow.value = newTotal
                } catch (e: Exception) {
                    LogManager.error("[$connectionId] Error writing to client socket: ${e.message}")
                    closeAll(webSocket, clientSocket)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                LogManager.error("[$connectionId] WS Failure ($targetHost:$targetPort): ${t.message}")
                handshakeDeferred.complete(false)
                closeAll(webSocket, clientSocket)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                LogManager.debug("[$connectionId] WS Closed ($code): $reason")
                closeAll(webSocket, clientSocket)
            }
        }

        activeWebSocket = okHttpClient.newWebSocket(request, listener)

        val handshakeSuccess = withTimeoutOrNull(15000) {
            handshakeDeferred.await()
        } ?: false

        if (!handshakeSuccess) {
            LogManager.error("[$connectionId] Handshake failed or timed out for $targetHost:$targetPort")
            // Send SOCKS5 General failure reply
            try {
                clientOut.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                clientOut.flush()
            } catch (e: Exception) {
                // ignore
            }
            closeAll(activeWebSocket, clientSocket)
            decrementConn()
            return
        }

        // Send SOCKS5 Success reply (0x05 0x00 0x00 0x01 0 0 0 0 0 0)
        try {
            clientOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            clientOut.flush()
        } catch (e: Exception) {
            LogManager.error("[$connectionId] Failed to write SOCKS5 success: ${e.message}")
            closeAll(activeWebSocket, clientSocket)
            decrementConn()
            return
        }

        // Upstream loop: read from client, encrypt, send to WS
        val upJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(65536)
            try {
                while (scope.isActive && !clientSocket.isClosed) {
                    val read = clientIn.read(buffer)
                    if (read <= 0) break
                    val encrypted = SangarCrypto.xorBuffer(buffer, key, read)
                    activeWebSocket?.send(encrypted.toByteString(0, read))
                    val newTotal = totalUploadBytes.addAndGet(read.toLong())
                    _uploadBytesFlow.value = newTotal
                }
            } catch (e: Exception) {
                // Connection ended normally or error
            } finally {
                closeAll(activeWebSocket, clientSocket)
                decrementConn()
            }
        }
    }

    private fun decrementConn() {
        val remaining = activeConnectionsCount.decrementAndGet()
        _connectionsFlow.value = remaining.coerceAtLeast(0)
    }

    private fun closeAll(ws: WebSocket?, socket: Socket?) {
        try {
            ws?.close(1000, "Normal closure")
        } catch (e: Exception) {
            // ignore
        }
        try {
            if (socket?.isClosed == false) {
                socket.close()
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
