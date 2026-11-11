package com.example.crypto

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SangarCrypto {

    private val secureRandom = SecureRandom()

    fun deriveKey(uuid: String, nonce: ByteArray): ByteArray {
        val tb = uuid.toByteArray(StandardCharsets.UTF_8)
        val key = ByteArray(16)
        for (i in 0 until 16) {
            key[i] = (tb[i % tb.size].toInt() xor nonce[i].toInt()).toByte()
        }
        return key
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /**
     * Builds the init packet matching the Python client protocol:
     * nonce (16b) + encrypted_body + mac (8b)
     */
    fun buildInit(
        uuid: String,
        host: String,
        port: Int,
        payload: ByteArray = ByteArray(0)
    ): Pair<ByteArray, ByteArray> {
        val nonce = ByteArray(16)
        secureRandom.nextBytes(nonce)
        val key = deriveKey(uuid, nonce)

        // Determine address type: 1 = IPv4, 3 = IPv6, 2 = Domain
        val at: Byte
        val addr: ByteArray

        val ipv4Bytes = tryParseIpv4(host)
        if (ipv4Bytes != null) {
            at = 1
            addr = ipv4Bytes
        } else {
            val ipv6Bytes = tryParseIpv6(host)
            if (ipv6Bytes != null) {
                at = 3
                addr = ipv6Bytes
            } else {
                val hb = host.toByteArray(StandardCharsets.UTF_8)
                at = 2
                addr = ByteArray(1 + hb.size).apply {
                    this[0] = hb.size.toByte()
                    System.arraycopy(hb, 0, this, 1, hb.size)
                }
            }
        }

        val bodyStream = ByteArrayOutputStream()
        bodyStream.write(1) // version 1
        bodyStream.write((port ushr 8) and 0xFF) // port high byte
        bodyStream.write(port and 0xFF) // port low byte
        bodyStream.write(at.toInt())
        bodyStream.write(addr)
        if (payload.isNotEmpty()) {
            bodyStream.write(payload)
        }
        val body = bodyStream.toByteArray()

        // Encrypt body using repeated 16-byte key
        val enc = ByteArray(body.size)
        for (i in body.indices) {
            enc[i] = (body[i].toInt() xor key[i % 16].toInt()).toByte()
        }

        // HMAC-SHA256 over enc, truncated to 8 bytes
        val fullMac = hmacSha256(key, enc)
        val mac8 = fullMac.copyOfRange(0, 8)

        // init packet: nonce (16) + enc + mac (8)
        val packetStream = ByteArrayOutputStream()
        packetStream.write(nonce)
        packetStream.write(enc)
        packetStream.write(mac8)

        return Pair(packetStream.toByteArray(), key)
    }

    /**
     * Fast buffer XOR cipher with the 16-byte derived key.
     */
    fun xorBuffer(buf: ByteArray, key: ByteArray, length: Int = buf.size): ByteArray {
        val out = ByteArray(length)
        for (i in 0 until length) {
            out[i] = (buf[i].toInt() xor key[i % 16].toInt()).toByte()
        }
        return out
    }

    /**
     * Verifies the server WebSocket ack packet.
     * Expects decrypted first 16 bytes to match HMAC-SHA256(key, "OK")[0..15].
     */
    fun verifyAck(ackBytes: ByteArray, key: ByteArray): Boolean {
        if (ackBytes.size < 16) return false
        val decrypted = xorBuffer(ackBytes, key, ackBytes.size)
        val expected = hmacSha256(key, "OK".toByteArray(StandardCharsets.UTF_8)).copyOfRange(0, 16)
        val actual = decrypted.copyOfRange(0, 16)
        return actual.contentEquals(expected)
    }

    private fun tryParseIpv4(host: String): ByteArray? {
        val parts = host.split(".")
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for (i in 0..3) {
            val num = parts[i].toIntOrNull() ?: return null
            if (num !in 0..255) return null
            bytes[i] = num.toByte()
        }
        return bytes
    }

    private fun tryParseIpv6(host: String): ByteArray? {
        if (!host.contains(":")) return null
        return try {
            val addr = java.net.Inet6Address.getByName(host)
            addr.address
        } catch (e: Exception) {
            null
        }
    }
}
