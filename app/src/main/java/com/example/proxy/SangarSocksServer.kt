package com.example.proxy

import com.example.data.LogManager
import com.example.model.ProxyProfile
import com.example.tunnel.SangarTunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class SangarSocksServer(
    val profile: ProxyProfile,
    private val scope: CoroutineScope
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    val tunnelManager = SangarTunnelManager(profile, scope)

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    fun start(): Boolean {
        if (_isServerRunning.value) return true

        try {
            val listenAddress = InetAddress.getByName("127.0.0.1")
            val s = ServerSocket(profile.localPort, 128, listenAddress)
            s.reuseAddress = true
            serverSocket = s
            _isServerRunning.value = true

            LogManager.success("SOCKS5 Server listening on 127.0.0.1:${profile.localPort}")
            LogManager.info("Target Worker: ${profile.getCleanWorkerHost()} (Clean IP: ${profile.cleanIp.ifBlank { "Domain Default" }})")

            serverJob = scope.launch(Dispatchers.IO) {
                while (isActive && !s.isClosed) {
                    try {
                        val client = s.accept()
                        // Safe socket buffers
                        try {
                            client.tcpNoDelay = true
                            client.sendBufferSize = 1048576
                            client.receiveBufferSize = 1048576
                        } catch (e: Exception) {
                            // ignore socket opt failures
                        }

                        scope.launch(Dispatchers.IO) {
                            handleClient(client)
                        }
                    } catch (e: SocketException) {
                        // Server socket closed
                        break
                    } catch (e: Exception) {
                        if (isActive) {
                            LogManager.error("Accept error: ${e.message}")
                        }
                    }
                }
                _isServerRunning.value = false
            }
            return true
        } catch (e: Exception) {
            LogManager.error("Failed to start server on port ${profile.localPort}: ${e.message}")
            _isServerRunning.value = false
            return false
        }
    }

    private suspend fun handleClient(client: Socket) {
        try {
            val dis = DataInputStream(client.getInputStream())
            val dos = DataOutputStream(client.getOutputStream())

            // 1. Handshake greeting
            val ver = dis.read()
            if (ver != 5) {
                client.close()
                return
            }

            val nmethods = dis.read()
            if (nmethods <= 0) {
                client.close()
                return
            }

            val methods = ByteArray(nmethods)
            dis.readFully(methods)

            // Reply NO_AUTH (0x05 0x00)
            dos.write(byteArrayOf(0x05, 0x00))
            dos.flush()

            // 2. Request details
            val reqVer = dis.read()
            val cmd = dis.read()
            val rsv = dis.read()
            val atyp = dis.read()

            if (reqVer != 5 || cmd != 1) { // 1 = CONNECT
                dos.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0)) // Command not supported
                dos.flush()
                client.close()
                return
            }

            val host: String
            when (atyp) {
                1 -> { // IPv4
                    val ip = ByteArray(4)
                    dis.readFully(ip)
                    host = InetAddress.getByAddress(ip).hostAddress ?: "0.0.0.0"
                }
                3 -> { // Domain name
                    val len = dis.read()
                    val domainBytes = ByteArray(len)
                    dis.readFully(domainBytes)
                    host = String(domainBytes, Charsets.UTF_8)
                }
                4 -> { // IPv6
                    val ip = ByteArray(16)
                    dis.readFully(ip)
                    host = InetAddress.getByAddress(ip).hostAddress ?: "::"
                }
                else -> {
                    dos.write(byteArrayOf(0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0)) // Address type not supported
                    dos.flush()
                    client.close()
                    return
                }
            }

            val portHigh = dis.read()
            val portLow = dis.read()
            val port = ((portHigh and 0xFF) shl 8) or (portLow and 0xFF)

            // Forward through tunnel manager
            tunnelManager.handleConnection(client, host, port)
        } catch (e: Exception) {
            try {
                if (!client.isClosed) client.close()
            } catch (ignored: Exception) {}
        }
    }

    fun stop() {
        _isServerRunning.value = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverJob?.cancel()
        LogManager.warn("SOCKS5 Server stopped")
    }
}
