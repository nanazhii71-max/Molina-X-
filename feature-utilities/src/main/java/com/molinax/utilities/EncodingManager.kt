package com.molinax.utilities

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object EncodingManager {

    fun base64Encode(input: String): String {
        return try {
            Base64.encodeToString(input.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun base64Decode(input: String): String {
        return try {
            val bytes = Base64.decode(input, Base64.DEFAULT)
            String(bytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            "Invalid Base64 string"
        }
    }

    fun urlEncode(input: String): String {
        return try {
            URLEncoder.encode(input, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun urlDecode(input: String): String {
        return try {
            URLDecoder.decode(input, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun hexDump(input: String): String {
        val bytes = input.toByteArray(StandardCharsets.UTF_8)
        val sb = StringBuilder()
        for (i in bytes.indices step 16) {
            sb.append("%08X  ".format(i))
            val chunk = bytes.copyOfRange(i, (i + 16).coerceAtMost(bytes.size))
            for (j in 0 until 16) {
                if (j < chunk.size) {
                    sb.append("%02X ".format(chunk[j]))
                } else {
                    sb.append("   ")
                }
                if (j == 7) sb.append(" ")
            }
            sb.append(" |")
            for (b in chunk) {
                val c = b.toInt().toChar()
                if (c in ' '..'~') sb.append(c) else sb.append('.')
            }
            sb.append("|\n")
        }
        return sb.toString()
    }

    fun formatJson(input: String, indentSpaces: Int = 4): String {
        val trimmed = input.trim()
        return try {
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).toString(indentSpaces)
            } else if (trimmed.startsWith("[")) {
                JSONArray(trimmed).toString(indentSpaces)
            } else {
                "Not a valid JSON object or array"
            }
        } catch (e: Exception) {
            "JSON Syntax Error: ${e.message}"
        }
    }
}
