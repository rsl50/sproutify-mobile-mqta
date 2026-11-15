package app.vereda.bff.jit.infrastructure

import app.vereda.bff.jit.domain.FundingMessage
import app.vereda.bff.jit.domain.money
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal

object JitMessageParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): List<FundingMessage> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val transactions = root["transactions"] as? JsonArray
        val objects = transactions?.mapNotNull { it as? JsonObject } ?: listOf(root)
        return objects.mapNotNull { parseOne(it) }
    }

    private fun parseOne(root: JsonObject): FundingMessage? {
        val order = root.obj("gpa_order") ?: root.obj("gpa_order_unload") ?: return null
        val jit = order.obj("jit_funding") ?: return null
        val token = jit.string("token") ?: return null
        val method = jit.string("method") ?: return null
        val user = jit.string("user_token") ?: root.string("user_token") ?: return null
        return FundingMessage(
            token = token,
            method = method,
            userToken = user,
            amount = jit.decimal("amount"),
            originalToken = jit.string("original_jit_funding_token"),
            impacted = root.obj("gpa")?.decimal("impacted_amount"),
            timedOut = order.obj("funding")?.obj("gateway_log")?.boolean("timed_out") == true,
        )
    }

    private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.boolean(name: String): Boolean =
        (this[name] as? JsonPrimitive)?.contentOrNull == "true"

    private fun JsonObject.decimal(name: String): BigDecimal? {
        val raw = (this[name] as? JsonPrimitive)?.contentOrNull ?: return null
        return runCatching { BigDecimal(raw).money() }.getOrNull()
    }
}
