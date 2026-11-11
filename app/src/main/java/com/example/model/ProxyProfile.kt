package com.example.model

import org.json.JSONObject
import java.util.UUID

data class ProxyProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uuid: String,
    val workerUrl: String,
    val cleanIp: String = "",
    val localPort: Int = 40446,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getCleanWorkerHost(): String {
        var clean = workerUrl.trim()
        if (clean.startsWith("wss://", ignoreCase = true)) {
            clean = clean.substring(6)
        } else if (clean.startsWith("ws://", ignoreCase = true)) {
            clean = clean.substring(5)
        } else if (clean.startsWith("https://", ignoreCase = true)) {
            clean = clean.substring(8)
        } else if (clean.startsWith("http://", ignoreCase = true)) {
            clean = clean.substring(7)
        }
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length - 1)
        }
        return clean
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("uuid", uuid)
            put("workerUrl", workerUrl)
            put("cleanIp", cleanIp)
            put("localPort", localPort)
            put("createdAt", createdAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ProxyProfile {
            return ProxyProfile(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Default Profile"),
                uuid = json.optString("uuid", ""),
                workerUrl = json.optString("workerUrl", ""),
                cleanIp = json.optString("cleanIp", ""),
                localPort = json.optInt("localPort", 40446),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
