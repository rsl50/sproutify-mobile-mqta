package app.vereda.trace

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

object TraceRedactor {
    const val MASK = "***"

    private val exact = setOf(
        "password",
        "passwd",
        "pwd",
        "pan",
        "primary_account_number",
        "card_number",
        "cvv",
        "cvv2",
        "cvc",
        "secret",
        "authorization",
        "application_token",
        "admin_access_token",
        "access_token",
        "session_token",
        "pin",
        "expiration",
        "expiration_date",
        "expiration_time",
        "expiry",
        "exp_date",
        "exp_month",
        "exp_year",
    )

    private val sensitiveHeaders = setOf(
        "authorization",
        "proxy-authorization",
        "x-session-token",
        "cookie",
        "set-cookie",
    )

    private val pan = Regex("""\d{13,19}""")
    private val basic = Regex("""(?i)\bBasic\s+[A-Za-z0-9+/=]+""")
    private val prettyJson = Json { prettyPrint = true }

    fun isSensitiveKey(name: String): Boolean {
        val key = name.trim().lowercase()
        if (key in exact) return true
        if (key == "token" || key.endsWith("_token")) return false
        return key.contains("password") ||
            key.contains("passwd") ||
            key.contains("secret") ||
            key.contains("cvv") ||
            key.contains("cvc") ||
            key == "pan" ||
            key.endsWith("_pan") ||
            key.contains("expir") ||
            key.contains("pin")
    }

    fun redactHeader(name: String, value: String): String {
        val key = name.trim().lowercase()
        if (key in sensitiveHeaders || isSensitiveKey(key)) return MASK
        return redactText(value)
    }

    fun redactHeaders(headers: Map<String, String>): Map<String, String> =
        headers.mapValues { (name, value) -> redactHeader(name, value) }

    fun redactBody(body: String?): String {
        if (body.isNullOrBlank()) return ""
        val element = runCatching { Json.parseToJsonElement(body) }.getOrNull()
        return if (element != null) {
            pretty(redactElement(element))
        } else {
            redactText(body)
        }
    }

    fun redactText(value: String): String = basic.replace(pan.replace(value) { MASK }, "Basic $MASK")

    private fun redactElement(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> buildJsonObject {
            element.forEach { (key, child) ->
                put(key, if (isSensitiveKey(key)) JsonPrimitive(MASK) else redactElement(child))
            }
        }
        is JsonArray -> buildJsonArray { element.forEach { add(redactElement(it)) } }
        is JsonNull -> JsonNull
        is JsonPrimitive -> if (element.isString) {
            JsonPrimitive(redactText(element.content))
        } else {
            val raw = element.content
            if (pan.matches(raw)) JsonPrimitive(MASK) else element
        }
    }

    private fun pretty(element: JsonElement): String = prettyJson.encodeToString(element)
}
