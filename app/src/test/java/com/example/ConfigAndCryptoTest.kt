package com.example

import com.example.crypto.SangarCrypto
import com.example.model.ConfigParser
import com.example.model.ProxyProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class ConfigAndCryptoTest {

    @Test
    fun testConfigParserAndSerializer() {
        val originalUri = "png://63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd@twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev?172.67.70.244%TwilightProfile"
        val parseResult = ConfigParser.parse(originalUri)
        assertTrue(parseResult.isSuccess)

        val profile = parseResult.getOrThrow()
        assertEquals("63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd", profile.uuid)
        assertEquals("twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev", profile.getCleanWorkerHost())
        assertEquals("172.67.70.244", profile.cleanIp)
        assertEquals("TwilightProfile", profile.name)

        val exported = ConfigParser.toConfigUri(profile)
        assertEquals(originalUri, exported)
    }

    @Test
    fun testConfigParserWithEncodedSpaces() {
        val uri = "png://my-uuid-123@myworker.dev?1.2.3.4%My%20Custom%20Profile"
        val result = ConfigParser.parse(uri)
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals("My Custom Profile", profile.name)
        assertEquals("1.2.3.4", profile.cleanIp)
        assertEquals("myworker.dev", profile.getCleanWorkerHost())
    }

    @Test
    fun testCryptoKeyDerivationAndXorSymmetry() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val nonce = ByteArray(16) { it.toByte() }
        val key = SangarCrypto.deriveKey(uuid, nonce)
        assertEquals(16, key.size)

        val sampleData = "Hello Secure Sangar Tunnel 1234567890".toByteArray(StandardCharsets.UTF_8)
        val encrypted = SangarCrypto.xorBuffer(sampleData, key)
        val decrypted = SangarCrypto.xorBuffer(encrypted, key)

        assertArrayEquals(sampleData, decrypted)
    }

    @Test
    fun testCryptoAckVerification() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val nonce = ByteArray(16) { (it * 3).toByte() }
        val key = SangarCrypto.deriveKey(uuid, nonce)

        // Expected decrypted ack from worker is HMAC-SHA256(key, "OK")[0..15]
        val expectedHmac = SangarCrypto.hmacSha256(key, "OK".toByteArray(StandardCharsets.UTF_8)).copyOfRange(0, 16)

        // Raw ack from websocket is encrypted with key
        val rawAck = SangarCrypto.xorBuffer(expectedHmac, key)

        assertTrue("Ack should verify successfully", SangarCrypto.verifyAck(rawAck, key))

        // Corrupt byte should fail
        val corruptedAck = rawAck.clone().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        assertFalse("Corrupted ack should fail verification", SangarCrypto.verifyAck(corruptedAck, key))
    }
}
