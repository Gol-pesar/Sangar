package com.example.model

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ConfigParser {

    /**
     * Parses a config URI formatted as:
     * png://UUID@WORKERURL?CLEANIP%PROFILENAME
     */
    fun parse(uriString: String): Result<ProxyProfile> {
        return try {
            val trimmed = uriString.trim()
            if (!trimmed.startsWith("png://", ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("Format must start with png://"))
            }

            val body = trimmed.substring(6).trim()
            if (!body.contains("@")) {
                return Result.failure(IllegalArgumentException("Missing '@' separator for UUID"))
            }

            val atSplit = body.split("@", limit = 2)
            val uuid = atSplit[0].trim()
            if (uuid.isEmpty()) {
                return Result.failure(IllegalArgumentException("UUID cannot be empty"))
            }

            val remainder = atSplit[1].trim()
            val workerUrl: String
            var cleanIp = ""
            var profileName = "Sangar Profile"

            if (remainder.contains("?")) {
                val qSplit = remainder.split("?", limit = 2)
                workerUrl = qSplit[0].trim()
                val paramPart = qSplit[1].trim()

                if (paramPart.contains("%")) {
                    val pSplit = paramPart.split("%", limit = 2)
                    cleanIp = pSplit[0].trim()
                    val rawName = pSplit[1].trim()
                    if (rawName.isNotEmpty()) {
                        profileName = try {
                            URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
                        } catch (e: Exception) {
                            rawName
                        }
                    }
                } else if (paramPart.contains("#")) {
                    // Fallback for # separator
                    val pSplit = paramPart.split("#", limit = 2)
                    cleanIp = pSplit[0].trim()
                    val rawName = pSplit[1].trim()
                    if (rawName.isNotEmpty()) {
                        profileName = try {
                            URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
                        } catch (e: Exception) {
                            rawName
                        }
                    }
                } else {
                    cleanIp = paramPart
                }
            } else {
                workerUrl = remainder
            }

            if (workerUrl.isEmpty()) {
                return Result.failure(IllegalArgumentException("Worker URL/host cannot be empty"))
            }

            Result.success(
                ProxyProfile(
                    name = profileName,
                    uuid = uuid,
                    workerUrl = workerUrl,
                    cleanIp = cleanIp
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Serializes a profile to the requested format:
     * png://UUID@WORKERURL?CLEANIP%PROFILENAME
     */
    fun toConfigUri(profile: ProxyProfile): String {
        val cleanHost = profile.getCleanWorkerHost()
        val encodedName = try {
            URLEncoder.encode(profile.name, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            profile.name
        }
        val cleanIp = profile.cleanIp.trim()
        return "png://${profile.uuid.trim()}@$cleanHost?$cleanIp%$encodedName"
    }
}
