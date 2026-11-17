package app.vereda.bff.jit.setup

import app.vereda.bff.config.MarqetaSettings
import app.vereda.bff.config.MarqetaSettingsLoader
import app.vereda.bff.infrastructure.marqeta.MarqetaCallFailed
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.infrastructure.marqeta.screenMessage
import app.vereda.bff.issuing.domain.CardFundingMode
import app.vereda.bff.jit.infrastructure.JitCredentials
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

data class JitRuntime(
    val credentials: JitCredentials?,
    val publicUrl: String?,
    val cardFunding: CardFundingMode,
    val cardProductToken: String,
    val fundingSourceToken: String,
    val webhookToken: String,
    val passwordProblem: String?,
)

fun main() {
    val code = runJitSandboxSetup()
    if (code != 0) kotlin.system.exitProcess(code)
}

fun runJitSandboxSetup(
    env: Map<String, String> = System.getenv(),
    files: List<File> = MarqetaSettingsLoader.defaultFiles(),
    execute: (MarqetaSettings, JitRuntime) -> Unit = ::configureSandbox,
): Int {
    val runtime = loadJitRuntime(env, files)
    val skip = setupSkipReason(env, files, runtime)
    if (skip != null) {
        println("jitSandboxSetup ignorado: $skip")
        return 0
    }
    val settings = MarqetaSettingsLoader.load(env, files)
    return try {
        execute(settings, runtime)
        0
    } catch (error: MarqetaCallFailed) {
        println("jitSandboxSetup falhou: ${error.screenMessage()}")
        1
    } catch (error: Exception) {
        println("jitSandboxSetup falhou: ${error.javaClass.simpleName}")
        1
    }
}

fun setupSkipReason(
    env: Map<String, String>,
    files: List<File>,
    runtime: JitRuntime = loadJitRuntime(env, files),
): String? {
    try {
        MarqetaSettingsLoader.load(env, files)
    } catch (error: IllegalStateException) {
        return error.message
    }
    if (runtime.credentials == null && runtime.passwordProblem != null) {
        return runtime.passwordProblem
    }
    if (runtime.credentials == null) {
        return "defina VEREDA_JIT_BASIC_USER e VEREDA_JIT_BASIC_PASSWORD."
    }
    val url = runtime.publicUrl
    if (url.isNullOrBlank()) {
        return "defina VEREDA_PUBLIC_URL com HTTPS (ngrok ou o deploy)."
    }
    if (!url.startsWith("https://")) {
        return "VEREDA_PUBLIC_URL precisa ser HTTPS."
    }
    return null
}

fun loadJitRuntime(
    env: Map<String, String> = System.getenv(),
    files: List<File> = MarqetaSettingsLoader.defaultFiles(),
): JitRuntime {
    val fileValues = linkedMapOf<String, String>()
    files.filter { it.isFile }.forEach { file ->
        fileValues.putAll(MarqetaSettingsLoader.parseKeyValues(file.readText()))
    }
    fun value(key: String): String? = env[key]?.trim()?.takeIf { it.isNotEmpty() }
        ?: fileValues[key]?.trim()?.takeIf { it.isNotEmpty() }
    val password = value("VEREDA_JIT_BASIC_PASSWORD")
    return JitRuntime(
        credentials = JitCredentials.load(env, files),
        publicUrl = value("VEREDA_PUBLIC_URL")?.trimEnd('/'),
        cardFunding = CardFundingMode.parse(value("VEREDA_CARD_FUNDING")),
        cardProductToken = value("VEREDA_JIT_CARD_PRODUCT") ?: "vereda_jit_card",
        fundingSourceToken = value("VEREDA_JIT_FUNDING_SOURCE") ?: "vereda_jit_gateway",
        webhookToken = value("VEREDA_JIT_WEBHOOK") ?: "vereda_jit_webhook",
        passwordProblem = password?.let(JitCredentials::policyProblem),
    )
}

private fun configureSandbox(settings: MarqetaSettings, runtime: JitRuntime) {
    val credentials = runtime.credentials ?: error("credenciais JIT ausentes")
    val publicUrl = runtime.publicUrl ?: error("URL pública ausente")
    val gatewayUrl = "$publicUrl/jit/gateway"
    val webhookUrl = "$publicUrl/jit/webhook"
    val api = MarqetaCoreApi.create(settings)
    try {
        runBlocking {
            ensureFundingSource(api, runtime.fundingSourceToken, gatewayUrl, credentials)
            ensureCardProduct(api, runtime.cardProductToken, runtime.fundingSourceToken)
            ensureWebhook(api, runtime.webhookToken, webhookUrl, credentials)
        }
        println("jitSandboxSetup ok")
        println("gateway=$gatewayUrl")
        println("webhook=$webhookUrl")
        println("cardProduct=${runtime.cardProductToken}")
        println("fundingSource=${runtime.fundingSourceToken}")
    } finally {
        api.close()
    }
}

private suspend fun ensureFundingSource(
    api: MarqetaCoreApi,
    token: String,
    gatewayUrl: String,
    credentials: JitCredentials,
) {
    val current = api.exchange("GET", "fundingsources/programgateway/$token")
    val matches = current.status == 200 && jsonField(current.body, "url") == gatewayUrl &&
        jsonField(current.body, "basic_auth_username") == credentials.username &&
        jsonField(current.body, "active") == "true"
    if (matches) return
    val payload = fundingSourceBody(token, gatewayUrl, credentials, includeIdentity = current.status != 200)
    val method = if (current.status == 200) "PUT" else "POST"
    val path = if (current.status == 200) "fundingsources/programgateway/$token" else "fundingsources/programgateway"
    val saved = api.exchange(method, path, payload)
    if (saved.status !in 200..299) throw MarqetaCallFailed(saved.status)
}

private suspend fun ensureCardProduct(api: MarqetaCoreApi, token: String, fundingSource: String) {
    val current = api.exchange("GET", "cardproducts/$token")
    if (current.status == 200 && gatewayRefunds(current.body) == "GATEWAY" &&
        gatewayFundingSource(current.body) == fundingSource
    ) {
        return
    }
    val payload = cardProductBody(token, fundingSource, includeIdentity = current.status != 200)
    val method = if (current.status == 200) "PUT" else "POST"
    val path = if (current.status == 200) "cardproducts/$token" else "cardproducts"
    val saved = api.exchange(method, path, payload)
    if (saved.status !in 200..299) throw MarqetaCallFailed(saved.status)
}

private suspend fun ensureWebhook(
    api: MarqetaCoreApi,
    token: String,
    webhookUrl: String,
    credentials: JitCredentials,
) {
    val current = api.exchange("GET", "webhooks/$token")
    val config = current.body.let { runCatching { Json.parseToJsonElement(it).jsonObject["config"] as? JsonObject }.getOrNull() }
    val matches = current.status == 200 &&
        config?.string("url") == webhookUrl &&
        config.string("basic_auth_username") == credentials.username &&
        jsonField(current.body, "active") == "true"
    if (matches) return
    val payload = webhookBody(token, webhookUrl, credentials)
    val method = if (current.status == 200) "PUT" else "POST"
    val path = if (current.status == 200) "webhooks/$token" else "webhooks"
    val saved = api.exchange(method, path, payload)
    if (saved.status !in 200..299) throw MarqetaCallFailed(saved.status)
}

internal fun fundingSourceBody(
    token: String,
    gatewayUrl: String,
    credentials: JitCredentials,
    includeIdentity: Boolean,
): String = buildJsonObject {
    if (includeIdentity) {
        put("token", token)
        put("name", "Vereda JIT")
    }
    put("url", gatewayUrl)
    put("basic_auth_username", credentials.username)
    put("basic_auth_password", credentials.password)
    put("active", true)
}.toString()

internal fun cardProductBody(token: String, fundingSource: String, includeIdentity: Boolean): String = buildJsonObject {
    if (includeIdentity) {
        put("token", token)
        put("name", "Vereda JIT")
        put("start_date", "2026-01-01")
    }
    putJsonObject("config") {
        if (includeIdentity) {
            putJsonObject("fulfillment") { put("payment_instrument", "VIRTUAL_PAN") }
            putJsonObject("poi") { put("ecommerce", true) }
            putJsonObject("card_life_cycle") { put("activate_upon_issue", true) }
        }
        putJsonObject("jit_funding") {
            putJsonObject("programgateway_funding_source") {
                put("enabled", true)
                put("funding_source_token", fundingSource)
                put("refunds_destination", "GATEWAY")
            }
        }
    }
}.toString()

internal fun webhookBody(token: String, webhookUrl: String, credentials: JitCredentials): String = buildJsonObject {
    put("token", token)
    put("name", "Vereda JIT")
    put("active", true)
    putJsonArray("events") { add(JsonPrimitive("*")) }
    putJsonObject("config") {
        put("url", webhookUrl)
        put("basic_auth_username", credentials.username)
        put("basic_auth_password", credentials.password)
    }
}.toString()

private fun gatewayRefunds(body: String): String? = runCatching {
    Json.parseToJsonElement(body).jsonObject["config"]?.jsonObject
        ?.get("jit_funding")?.jsonObject
        ?.get("programgateway_funding_source")?.jsonObject
        ?.get("refunds_destination")?.jsonPrimitive?.contentOrNull
}.getOrNull()

private fun gatewayFundingSource(body: String): String? = runCatching {
    Json.parseToJsonElement(body).jsonObject["config"]?.jsonObject
        ?.get("jit_funding")?.jsonObject
        ?.get("programgateway_funding_source")?.jsonObject
        ?.get("funding_source_token")?.jsonPrimitive?.contentOrNull
}.getOrNull()

private fun jsonField(body: String, name: String): String? = runCatching {
    Json.parseToJsonElement(body).jsonObject[name]?.jsonPrimitive?.contentOrNull
}.getOrNull()

private fun JsonObject?.string(name: String): String? = this?.get(name)?.jsonPrimitive?.contentOrNull
